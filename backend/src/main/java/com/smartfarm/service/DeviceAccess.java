package com.smartfarm.service;

import com.smartfarm.repository.FarmRepository;

import com.smartfarm.config.DeviceKey;
import com.smartfarm.domain.Actuator;
import com.smartfarm.domain.ControllerBoard;
import com.smartfarm.domain.FarmDevice;
import com.smartfarm.domain.Sensor;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.ControllerBoardRepository;
import com.smartfarm.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Who may send a reading or read a relay: a controller board's own key (only the devices assigned to that board),
 * or the server-wide device key FARM_DEVICE_KEY. Since one server holds many customer farms, the server-wide key
 * only reaches the original farm's devices (the farm the server started with), so a leaked key cannot write into
 * another customer's farm; farms made by the company use their boards' keys. A board that calls in is marked as seen.
 */
@Service
@Transactional
public class DeviceAccess {

    private final DeviceKey farmKey;
    private final ControllerBoardRepository boards;
    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final Clock clock;
    private final FarmRepository farms;

    public DeviceAccess(DeviceKey farmKey, ControllerBoardRepository boards, SensorRepository sensors,
                        ActuatorRepository actuators, Clock clock, FarmRepository farms) {
        this.farms = farms;
        this.farmKey = farmKey;
        this.boards = boards;
        this.sensors = sensors;
        this.actuators = actuators;
        this.clock = clock;
    }

    public void checkSensor(String key, Long sensorId) {
        Sensor sensor = sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId));
        if (farmKey.matches(key)) {
            requireOriginalFarm(sensor.getZone().getFarm().getId(), "sensor #" + sensorId);
            return;
        }
        ControllerBoard board = board(key);
        requireOwn(board, sensor.getDevice(), "sensor #" + sensorId);
    }

    public void checkActuator(String key, Long actuatorId) {
        Actuator actuator = actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId));
        if (farmKey.matches(key)) {
            requireOriginalFarm(actuator.getZone().getFarm().getId(), "actuator #" + actuatorId);
            return;
        }
        ControllerBoard board = board(key);
        requireOwn(board, actuator.getDevice(), "actuator #" + actuatorId);
    }

    private void requireOriginalFarm(Long farmId, String what) {
        Long original = farms.findFirstByOrderByIdAsc().map(f -> f.getId()).orElse(null);
        if (!farmId.equals(original)) {
            throw new ForbiddenException("The server-wide device key cannot reach " + what + " on this farm. Use the key of the controller board it is wired to.");
        }
    }

    private ControllerBoard board(String key) {
        ControllerBoard board = key == null ? null : boards.findByKeyHash(ControllerBoard.hash(key)).orElse(null);
        if (board == null) {
            throw new ForbiddenException("Invalid device key");
        }
        board.seen(clock.instant());
        return board;
    }

    private static void requireOwn(ControllerBoard board, FarmDevice device, String what) {
        if (device == null || device.getController() == null || !device.getController().getId().equals(board.getId())) {
            throw new ForbiddenException("Board " + board.getName() + " is not assigned to " + what);
        }
    }
}

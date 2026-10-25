package com.smartfarm.service;

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
 * Who may send a reading or read a relay: the farm's device key (any device, as before), or a controller
 * board's own key (only the devices assigned to that board). A board that calls in is marked as seen.
 */
@Service
@Transactional
public class DeviceAccess {

    private final DeviceKey farmKey;
    private final ControllerBoardRepository boards;
    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final Clock clock;

    public DeviceAccess(DeviceKey farmKey, ControllerBoardRepository boards, SensorRepository sensors,
                        ActuatorRepository actuators, Clock clock) {
        this.farmKey = farmKey;
        this.boards = boards;
        this.sensors = sensors;
        this.actuators = actuators;
        this.clock = clock;
    }

    public void checkSensor(String key, Long sensorId) {
        if (farmKey.matches(key)) {
            return;
        }
        ControllerBoard board = board(key);
        Sensor sensor = sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId));
        requireOwn(board, sensor.getDevice(), "sensor #" + sensorId);
    }

    public void checkActuator(String key, Long actuatorId) {
        if (farmKey.matches(key)) {
            return;
        }
        ControllerBoard board = board(key);
        Actuator actuator = actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId));
        requireOwn(board, actuator.getDevice(), "actuator #" + actuatorId);
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

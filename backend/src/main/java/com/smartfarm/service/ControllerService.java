package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.ControllerBoardRepository;
import com.smartfarm.repository.DeviceModelRepository;
import com.smartfarm.repository.FarmDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Controller boards (ESP32 nodes): each has its own device key, shown once, and only drives the devices
 * assigned to it. The firmware settings for a board are generated from its devices.
 */
@Service
@Transactional
public class ControllerService {

    /** A new board and the key to put in its firmware (the server keeps only a hash). */
    public record BoardWithKey(ControllerBoard board, String key) {
    }

    private final ControllerBoardRepository boards;
    private final DeviceModelRepository models;
    private final FarmDeviceRepository devices;
    private final AccessPolicy access;

    private final Tenancy tenancy;
    private final com.smartfarm.repository.FarmRepository farms;


    public ControllerService(ControllerBoardRepository boards, DeviceModelRepository models, FarmDeviceRepository devices,
                             AccessPolicy access, Tenancy tenancy, com.smartfarm.repository.FarmRepository farms) {

        this.tenancy = tenancy;
        this.farms = farms;
        this.boards = boards;
        this.models = models;
        this.devices = devices;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<ControllerBoard> list() {
        return tenancy.limited() ? boards.findByFarm_IdOrderByIdAsc(tenancy.farmId()) : boards.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public long deviceCount(Long boardId) {
        return devices.countByController_Id(boardId);
    }

    public BoardWithKey create(Long userId, String name, Long modelId, String notes) {
        access.require(userId, Role.TECHNICIAN);
        ControllerBoard board = new ControllerBoard(name.trim(), model(modelId), notes);
        board.setFarm(tenancy.limited() ? farms.findById(tenancy.farmId()).orElseThrow() : farms.findFirstByOrderByIdAsc().orElseThrow());
        String key = board.newKey();
        return new BoardWithKey(boards.save(board), key);
    }

    public ControllerBoard update(Long userId, Long id, String name, Long modelId, String notes) {
        access.require(userId, Role.TECHNICIAN);
        ControllerBoard board = board(id);
        board.update(name.trim(), model(modelId), notes);
        return board;
    }

    /** A new key for a lost or leaked board: the old one stops working at once. */
    public BoardWithKey newKey(Long userId, Long id) {
        access.require(userId, Role.TECHNICIAN);
        ControllerBoard board = board(id);
        return new BoardWithKey(board, board.newKey());
    }

    public void delete(Long userId, Long id) {
        access.require(userId, Role.TECHNICIAN);
        ControllerBoard board = board(id);
        for (FarmDevice device : devices.findByController_IdOrderByIdAsc(id)) {
            device.update(device.getName(), device.getSerialNumber(), device.getNotes(), null);
        }
        boards.delete(board);
    }

    /**
     * The secrets.h settings for a board: its key (only when just made) and the ids of its devices. The pins our
     * firmware knows (soil, temperature, pump) get their #define; other channels are listed as comments.
     */
    @Transactional(readOnly = true)
    public String firmware(Long id, String key) {
        ControllerBoard board = board(id);
        StringBuilder out = new StringBuilder();
        out.append("// secrets.h for ").append(board.getName()).append("\n");
        out.append("#define WIFI_SSID \"your-wifi-name\"\n#define WIFI_PASSWORD \"your-wifi-password\"\n");
        out.append("#define SERVER_URL \"http://<server-ip>:8080\"\n");
        out.append("#define DEVICE_KEY \"").append(key != null ? key : "<this board's key, shown when it was made>").append("\"\n\n");
        Long soil = null, temp = null, pump = null;
        StringBuilder others = new StringBuilder();
        for (FarmDevice device : devices.findByController_IdOrderByIdAsc(id)) {
            for (Sensor s : device.getSensors()) {
                if (soil == null && "SOIL_MOISTURE".equals(s.getMeasurementCode())) soil = s.getId();
                else if (temp == null && "TEMPERATURE".equals(s.getMeasurementCode())) temp = s.getId();
                else others.append("//   sensor #").append(s.getId()).append(" ").append(s.getName()).append(" (").append(s.getUnit()).append(")\n");
            }
            for (Actuator a : device.getActuators()) {
                if (pump == null && a.getType() == ActuatorType.WATER_PUMP) pump = a.getId();
                else others.append("//   actuator #").append(a.getId()).append(" ").append(a.getName()).append("\n");
            }
        }
        out.append("#define SOIL_SENSOR_ID ").append(soil == null ? 0 : soil).append("\n");
        out.append("#define TEMP_SENSOR_ID ").append(temp == null ? 0 : temp).append("\n");
        out.append("#define PUMP_ACTUATOR_ID ").append(pump == null ? 0 : pump).append("\n");
        out.append("#define USE_RELAY ").append(pump == null ? 0 : 1).append("\n");
        if (!others.isEmpty()) {
            out.append("\n// Other devices on this board (add code for them in the firmware):\n").append(others);
        }
        return out.toString();
    }

    private ControllerBoard board(Long id) {
        return tenancy.board(boards.findById(id).orElseThrow(() -> new NotFoundException("Controller board", id)));
    }

    private DeviceModel model(Long modelId) {
        return modelId == null ? null : models.findById(modelId).orElseThrow(() -> new NotFoundException("Device model", modelId));
    }
}

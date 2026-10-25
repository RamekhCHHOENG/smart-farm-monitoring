package com.smartfarm.web;

import com.smartfarm.service.ControllerService;
import com.smartfarm.service.ControllerService.BoardWithKey;
import com.smartfarm.web.Dtos.ControllerDto;
import com.smartfarm.web.Dtos.ControllerKeyDto;
import com.smartfarm.web.Dtos.ControllerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Controller boards (ESP32 nodes) with their own keys and generated firmware settings. */
@RestController
@RequestMapping("/api/controllers")
@Transactional
public class ControllerBoardController {

    private final ControllerService boards;

    public ControllerBoardController(ControllerService boards) {
        this.boards = boards;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ControllerDto> list() {
        return boards.list().stream().map(b -> ControllerDto.of(b, boards.deviceCount(b.getId()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ControllerKeyDto create(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody ControllerRequest req) {
        return withKey(boards.create(userId, req.name(), req.modelId(), req.notes()));
    }

    @PutMapping("/{id}")
    public ControllerDto update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                @Valid @RequestBody ControllerRequest req) {
        return ControllerDto.of(boards.update(userId, id, req.name(), req.modelId(), req.notes()), boards.deviceCount(id));
    }

    @PostMapping("/{id}/key")
    public ControllerKeyDto newKey(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return withKey(boards.newKey(userId, id));
    }

    /** The board's firmware settings without its key (the key is shown only when made). */
    @GetMapping("/{id}/firmware")
    @Transactional(readOnly = true)
    public Map<String, String> firmware(@PathVariable Long id) {
        return Map.of("firmware", boards.firmware(id, null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        boards.delete(userId, id);
    }

    private ControllerKeyDto withKey(BoardWithKey made) {
        Long id = made.board().getId();
        return new ControllerKeyDto(ControllerDto.of(made.board(), boards.deviceCount(id)), made.key(), boards.firmware(id, made.key()));
    }
}

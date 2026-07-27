package com.api.atlas.controller;

import com.api.atlas.model.ApiInterface;
import com.api.atlas.model.ApiInterfaceCreateDTO;
import com.api.atlas.model.ApiInterfaceUpdateDTO;
import com.api.atlas.model.R;
import com.api.atlas.service.ApiInterfaceService;
import com.api.atlas.service.executor.QueryResult;
import com.github.pagehelper.PageInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/interfaces")
public class InterfaceController {

    private final ApiInterfaceService apiInterfaceService;

    public InterfaceController(ApiInterfaceService apiInterfaceService) {
        this.apiInterfaceService = apiInterfaceService;
    }

    @PostMapping
    public R<ApiInterface> create(@Valid @RequestBody ApiInterfaceCreateDTO dto) {
        return R.created(apiInterfaceService.create(dto));
    }

    @GetMapping
    public R<List<ApiInterface>> list(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @Valid @RequestParam(defaultValue = "10") @Max(1000) int pageSize) {
        PageInfo<ApiInterface> page = apiInterfaceService.list(dataSourceId, name, status, pageNum, pageSize);
        return R.ok(page.getList(), page);
    }

    @GetMapping("/{id}")
    public R<ApiInterface> getById(@PathVariable Long id) {
        return R.ok(apiInterfaceService.getById(id));
    }

    @PutMapping("/{id}")
    public R<ApiInterface> update(@PathVariable Long id, @Valid @RequestBody ApiInterfaceUpdateDTO dto) {
        return R.ok(apiInterfaceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        apiInterfaceService.delete(id);
        return R.deleted();
    }

    @PostMapping("/{id}/test")
    public R<QueryResult> test(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", new HashMap<>());
        int pageNum = body.get("pageNum") instanceof Number n ? n.intValue() : 0;
        int pageSize = body.get("pageSize") instanceof Number n ? n.intValue() : 0;
        QueryResult result = apiInterfaceService.testInterface(id, params, pageNum, pageSize);
        return R.ok(result);
    }

    @PatchMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        apiInterfaceService.updateStatus(id, body.get("status"));
        return R.ok(null);
    }
}

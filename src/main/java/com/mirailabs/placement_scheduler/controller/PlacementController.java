package com.mirailabs.placement_scheduler.controller;


import com.mirailabs.placement_scheduler.dto.CompanyDelayRequest;
import com.mirailabs.placement_scheduler.dto.PanelDropRequest;
import com.mirailabs.placement_scheduler.dto.RoomBlockRequest;
import com.mirailabs.placement_scheduler.dto.StudentWithdrawalRequest;
import com.mirailabs.placement_scheduler.model.Interview;
import com.mirailabs.placement_scheduler.replan.ReplanResult;
import com.mirailabs.placement_scheduler.scheduler.SchedulingResult;
import com.mirailabs.placement_scheduler.scheduler.UnscheduledEntry;
import com.mirailabs.placement_scheduler.service.SchedulingStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * The 7 minimal endpoints needed for the coordinator dashboard. Every
 * method is thin delegation to SchedulingStateService - no scheduling or
 * replanning logic lives here, only HTTP plumbing.
 */
@RestController
@RequestMapping("/api")
public class PlacementController {

    private final SchedulingStateService service;

    public PlacementController(SchedulingStateService service) {
        this.service = service;
    }

    @PostMapping("/schedule/generate")
    public SchedulingResult generate() {
        return service.generate();
    }

    @GetMapping("/schedule/interviews")
    public List<Interview> interviews() {
        return service.getCurrentSchedule();
    }

    @GetMapping("/schedule/unscheduled")
    public List<UnscheduledEntry> unscheduled() {
        return service.getUnscheduledEntries();
    }

    @PostMapping("/replan/panel-drop")
    public ReplanResult panelDrop(@RequestBody PanelDropRequest request) {
        return service.panelDrop(request.getPanelId(), request.getDay(), request.getUnit());
    }

    @PostMapping("/replan/room-block")
    public ReplanResult roomBlock(@RequestBody RoomBlockRequest request) {
        return service.roomBlock(request.getRoomId(), request.getDay(), request.getUnit());
    }

    @PostMapping("/replan/student-withdrawal")
    public ReplanResult studentWithdrawal(@RequestBody StudentWithdrawalRequest request) {
        return service.studentWithdrawal(request.getStudentId(), request.getDay(), request.getUnit());
    }

    @PostMapping("/replan/company-delay")
    public ReplanResult companyDelay(@RequestBody CompanyDelayRequest request) {
        return service.companyDelay(request.getCompanyId(), request.getDelayUntilUnit());
    }

    // Minimal safety net: if generate() hasn't been called yet, return a
    // clean 400 with a clear message instead of a raw 500 stack trace.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}

package com.java_template.common.grpc;

import com.java_template.common.grpc.client.connection.ConnectionManager;
import com.java_template.common.grpc.client.monitoring.GrpcConnectionMonitor;
import io.grpc.ConnectivityState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/grpc")
public class GrpcAdminController {

    private final ConnectionManager connectionManager;
    private final GrpcConnectionMonitor connectionMonitor;


    public GrpcAdminController(
            final ConnectionManager connectionManager,
            final GrpcConnectionMonitor connectionMonitor
    ) {
        this.connectionManager = connectionManager;
        this.connectionMonitor = connectionMonitor;
    }

    @PostMapping("/reconnect")
    public ResponseEntity<String> resurrect(@RequestParam("force") boolean force) {
        if(connectionMonitor.getLastKnownState().connectionState().equals(ConnectivityState.IDLE)) {
            connectionManager.resurrect();
            return ResponseEntity.ok("Reconnection from IDLE state initiated");
        } else if (force) {
            connectionManager.resurrect();
            return ResponseEntity.ok("Reconnection initiated with system in state: " + connectionMonitor.getLastKnownState());
        } else {
            return ResponseEntity.badRequest().body("Not in idle state");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<GrpcConnectionMonitor.GrpcMonitoringState> getStatus() {
        return ResponseEntity.ok(connectionMonitor.getLastKnownState());
    }

}
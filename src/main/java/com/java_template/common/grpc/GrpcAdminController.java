package com.java_template.common.grpc;

import com.java_template.common.grpc.client.connection.ConnectionManager;
import com.java_template.common.grpc.client.monitoring.GrpcConnectionMonitor;
import com.java_template.common.tool.CyodaInit;
import com.java_template.common.tool.CyodaInitConfig;
import io.grpc.ConnectivityState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/grpc")
public class GrpcAdminController {

    private final ConnectionManager connectionManager;
    private final GrpcConnectionMonitor connectionMonitor;
    private final CyodaInit cyodaInit;


    public GrpcAdminController(
            final ConnectionManager connectionManager,
            final GrpcConnectionMonitor connectionMonitor,
            final CyodaInit cyodaInit
    ) {
        this.connectionManager = connectionManager;
        this.connectionMonitor = connectionMonitor;
        this.cyodaInit = cyodaInit;
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

    @PostMapping("/import-workflows")
    public ResponseEntity<String> importWorkflows() {
        try {
            CyodaInitConfig config = new CyodaInitConfig();
            cyodaInit.initCyoda(config);
            return ResponseEntity.ok("Workflows and entities import initiated successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to import workflows: " + e.getMessage());
        }
    }

}
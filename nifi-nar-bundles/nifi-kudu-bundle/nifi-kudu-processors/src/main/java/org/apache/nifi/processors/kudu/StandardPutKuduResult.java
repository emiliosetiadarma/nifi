package org.apache.nifi.processors.kudu;

import org.apache.kudu.client.Operation;
import org.apache.kudu.client.RowError;
import org.apache.nifi.flowfile.FlowFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardPutKuduResult extends PutKuduResult {
    private final Map<Operation, FlowFile> operationFlowFileMap;
    private final List<RowError> pendingRowErrors;
    private final Map<FlowFile, List<RowError>> flowFileRowErrorsMap;

    public StandardPutKuduResult() {
        super();
        this.operationFlowFileMap = new HashMap<>();
        this.pendingRowErrors = new ArrayList<>();
        this.flowFileRowErrorsMap = new HashMap<>();
    }

    @Override
    public void recordOperation(final Operation operation) {
        operationFlowFileMap.put(operation, flowFile);
    }

    @Override
    public void addError(final RowError rowError) {
        // When this class is used to store results from processing FlowFiles, the FlushMode
        // is set to AUTO_FLUSH_BACKGROUND or MANUAL_FLUSH. In either case, we won't know which
        // FlowFile/Record we are currently processing as the RowErrors are obtained from the KuduSession
        // post-processing of the FlowFile/Record
        this.pendingRowErrors.add(rowError);
    }

    @Override
    public void resolveFlowFileToRowErrorAssociations() {
        flowFileRowErrorsMap.putAll(pendingRowErrors.stream()
                .filter(e -> operationFlowFileMap.get(e.getOperation()) != null)
                .collect(
                        Collectors.groupingBy(e -> operationFlowFileMap.get(e.getOperation()))
                )
        );

        pendingRowErrors.clear();
    }

    @Override
    public boolean hasFailures() {
        if (!flowFileExceptionMap.isEmpty()) {
            return true;
        }

        for (final Map.Entry<FlowFile, List<RowError>> entry : flowFileRowErrorsMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<RowError> getRowErrorsForFlowFile(final FlowFile flowFile) {
        return flowFileRowErrorsMap.getOrDefault(flowFile, Collections.EMPTY_LIST);
    }
}

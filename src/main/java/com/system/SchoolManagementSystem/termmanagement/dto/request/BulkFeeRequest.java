package com.system.SchoolManagementSystem.termmanagement.dto.request;

import com.system.SchoolManagementSystem.termmanagement.entity.TermFeeItem;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class BulkFeeRequest {
    private List<Long> studentIds;
    private Long termId;
    private String itemName;
    private TermFeeItem.FeeType feeType;
    private Double amount;
    private LocalDate dueDate;
    private Boolean isMandatory = true;
    private String notes;
}
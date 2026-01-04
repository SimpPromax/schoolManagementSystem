package com.system.SchoolManagementSystem.demo.service;

import com.system.SchoolManagementSystem.config.TenantContext;
import com.system.SchoolManagementSystem.demo.dto.DemoRequest;
import com.system.SchoolManagementSystem.demo.dto.DemoResponse;
import com.system.SchoolManagementSystem.demo.entity.DemoEntity;
import com.system.SchoolManagementSystem.demo.repository.DemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoService {

    private final DemoRepository demoRepository;

    @Transactional
    public DemoResponse createDemo(DemoRequest request) {
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Creating demo entity for tenant: {}", tenantId);

        DemoEntity demoEntity = DemoEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .value(request.getValue())
                .category(request.getCategory())
                .tenantId(tenantId)
                .createdBy("demo-user") // In real app, get from SecurityContext
                .build();

        DemoEntity savedEntity = demoRepository.save(demoEntity);

        log.info("Demo entity created with ID: {} for tenant: {}", savedEntity.getId(), tenantId);

        return mapToResponse(savedEntity);
    }

    public List<DemoResponse> getAllDemos() {
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Fetching all demos for tenant: {}", tenantId);

        List<DemoEntity> demos = demoRepository.findByTenantId(tenantId);

        return demos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public DemoResponse getDemoById(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Fetching demo with ID: {} for tenant: {}", id, tenantId);

        DemoEntity demoEntity = demoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demo not found with id: " + id));

        // Ensure the demo belongs to the current tenant
        if (!demoEntity.getTenantId().equals(tenantId) && !"master".equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to demo entity");
        }

        return mapToResponse(demoEntity);
    }

    @Transactional
    public DemoResponse updateDemo(Long id, DemoRequest request) {
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Updating demo with ID: {} for tenant: {}", id, tenantId);

        DemoEntity demoEntity = demoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demo not found with id: " + id));

        // Ensure the demo belongs to the current tenant
        if (!demoEntity.getTenantId().equals(tenantId) && !"master".equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to demo entity");
        }

        demoEntity.setName(request.getName());
        demoEntity.setDescription(request.getDescription());
        demoEntity.setValue(request.getValue());
        demoEntity.setCategory(request.getCategory());
        demoEntity.setIsActive(request.getIsActive());

        DemoEntity updatedEntity = demoRepository.save(demoEntity);

        return mapToResponse(updatedEntity);
    }

    @Transactional
    public void deleteDemo(Long id) {
        String tenantId = TenantContext.getCurrentTenant();

        log.info("Deleting demo with ID: {} for tenant: {}", id, tenantId);

        DemoEntity demoEntity = demoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demo not found with id: " + id));

        // Ensure the demo belongs to the current tenant
        if (!demoEntity.getTenantId().equals(tenantId) && !"master".equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to demo entity");
        }

        demoRepository.delete(demoEntity);

        log.info("Demo deleted successfully for tenant: {}", tenantId);
    }

    public String testDatabaseConnection() {
        String tenantId = TenantContext.getCurrentTenant();

        try {
            long count = demoRepository.countByTenantId(tenantId);
            return String.format("Database connection successful! Tenant: %s, Demo count: %d",
                    tenantId, count);
        } catch (Exception e) {
            throw new RuntimeException("Database connection failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getDemoStatistics() {
        String tenantId = TenantContext.getCurrentTenant();

        long totalCount = demoRepository.countByTenantId(tenantId);
        long activeCount = demoRepository.countActiveByTenantId(tenantId);
        List<String> categories = demoRepository.findDistinctCategoriesByTenantId(tenantId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("tenantId", tenantId);
        stats.put("totalDemos", totalCount);
        stats.put("activeDemos", activeCount);
        stats.put("inactiveDemos", totalCount - activeCount);
        stats.put("categories", categories);
        stats.put("categoriesCount", categories.size());
        stats.put("timestamp", java.time.LocalDateTime.now());

        return stats;
    }

    private DemoResponse mapToResponse(DemoEntity entity) {
        return DemoResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .value(entity.getValue())
                .category(entity.getCategory())
                .isActive(entity.getIsActive())
                .tenantId(entity.getTenantId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
package com.dejebu.service;

import com.dejebu.entity.HospitalEntity;
import com.dejebu.repository.HospitalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class HospitalService {

    private static final String DEFAULT_HOSPITAL_ID = "hospital_xuchang";

    private final HospitalRepository hospitalRepository;

    public HospitalService(HospitalRepository hospitalRepository) {
        this.hospitalRepository = hospitalRepository;
    }

    public record HospitalLocation(
            String id,
            String mapId,
            String name,
            int respawnX,
            int respawnY
    ) {}

    @Transactional(readOnly = true)
    public HospitalLocation findNearest(String mapId, int x, int y) {
        List<HospitalEntity> hospitals = hospitalRepository.findAllByOrderByIdAsc();
        if (hospitals.isEmpty()) {
            throw new IllegalStateException("尚未設定任何醫館");
        }

        return hospitals.stream()
                .min(Comparator
                        .comparingInt((HospitalEntity hospital) -> sameMapPriority(hospital, mapId))
                        .thenComparingInt(hospital -> manhattanDistance(hospital, mapId, x, y))
                        .thenComparing(HospitalEntity::getId))
                .map(this::toLocation)
                .orElseGet(() -> toLocation(hospitals.get(0)));
    }

    @Transactional(readOnly = true)
    public HospitalLocation getDefaultHospital() {
        return hospitalRepository.findById(DEFAULT_HOSPITAL_ID)
                .map(this::toLocation)
                .orElseGet(() -> hospitalRepository.findAllByOrderByIdAsc().stream()
                        .findFirst()
                        .map(this::toLocation)
                        .orElseThrow(() -> new IllegalStateException("尚未設定任何醫館")));
    }

    @Transactional(readOnly = true)
    public boolean isHospitalNpc(String npcId) {
        return npcId.endsWith("_healer");
    }

    private int sameMapPriority(HospitalEntity hospital, String mapId) {
        return hospital.getMapId().equals(mapId) ? 0 : 1;
    }

    private int manhattanDistance(HospitalEntity hospital, String mapId, int x, int y) {
        if (!hospital.getMapId().equals(mapId)) {
            return 10_000;
        }
        return Math.abs(hospital.getRespawnX() - x) + Math.abs(hospital.getRespawnY() - y);
    }

    private HospitalLocation toLocation(HospitalEntity hospital) {
        return new HospitalLocation(
                hospital.getId(),
                hospital.getMapId(),
                hospital.getName(),
                hospital.getRespawnX(),
                hospital.getRespawnY()
        );
    }
}

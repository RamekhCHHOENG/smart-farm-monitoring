package com.smartfarm.domain;

import jakarta.persistence.*;

/**
 * Master data: something a sensor can measure (temperature in °C, CO2 in ppm, pH...). The farm owner can add
 * new ones without code; the built-in ones match the sensor classes that existed before the catalog.
 * Information Expert: it knows its unit and its physical range.
 */
@Entity
@Table(name = "measurement_type")
public class MeasurementType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable key, upper case: TEMPERATURE, CO2, PH... */
    @Column(unique = true, nullable = false, length = 40)
    private String code;

    private String name;
    private String unit;
    private double minValid;
    private double maxValid;
    /** A healthy, ordinary value: where the simulator starts and drifts back to. */
    private double typical;
    private boolean builtIn;

    /** null = shared by every farm (kept by company staff); a farm id = a measurement one farm added for itself. */
    private Long farmId;

    protected MeasurementType() {
    }

    public MeasurementType(String code, String name, String unit, double minValid, double maxValid, double typical, boolean builtIn) {
        this.code = code;
        this.builtIn = builtIn;
        update(name, unit, minValid, maxValid, typical);
    }

    public void update(String name, String unit, double minValid, double maxValid, double typical) {
        if (!(minValid < maxValid)) {
            throw new IllegalArgumentException("The lowest valid value must be below the highest");
        }
        if (typical < minValid || typical > maxValid) {
            throw new IllegalArgumentException("The typical value must be inside the valid range");
        }
        this.name = name;
        this.unit = unit;
        this.minValid = minValid;
        this.maxValid = maxValid;
        this.typical = typical;
    }

    /** "Soil CO₂ level" → "SOIL_CO_LEVEL": a code made from a name. */
    public static String codeFrom(String name) {
        String code = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Give the measurement a name with letters or digits");
        }
        return code.length() > 40 ? code.substring(0, 40) : code;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public double getMinValid() { return minValid; }
    public double getMaxValid() { return maxValid; }
    public double getTypical() { return typical; }
    public boolean isBuiltIn() { return builtIn; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
}

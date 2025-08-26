package com.java_template.application.entity.pet.version_1;

import java.util.Objects;

public class Pet {
    private String id;
    private String name;
    private String status;

    public Pet() {
    }

    public Pet(String id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pet pet = (Pet) o;
        return Objects.equals(id, pet.id) &&
               Objects.equals(name, pet.name) &&
               Objects.equals(status, pet.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, status);
    }

    @Override
    public String toString() {
        return "Pet{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", status='" + status + '\'' +
               '}';
    }
}
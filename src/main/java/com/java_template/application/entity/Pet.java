import lombok.Data;
import com.java_template.common.entity.CyodaEntity;
import com.java_template.common.entity.OperationSpecification;
import com.java_template.common.entity.ModelSpec;

@Data
public class Pet implements CyodaEntity {
    private java.util.UUID technicalId;
    private Long id;
    private String name;
    private String category;
    private String status;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt("1"));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        if (id != null && id < 1) return false;
        if (name == null || name.trim().isEmpty()) return false;
        if (category == null || category.trim().isEmpty()) return false;
        if (status == null) return false;
        String lowerStatus = status.toLowerCase();
        return lowerStatus.equals("available") || lowerStatus.equals("pending") || lowerStatus.equals("sold");
    }
}

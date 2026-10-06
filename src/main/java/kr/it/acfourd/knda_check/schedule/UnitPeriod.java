package kr.it.acfourd.knda_check.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import kr.it.acfourd.knda_check.common.ClassCode;

@Entity
@Table(name = "unit_period")
public class UnitPeriod {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_code", nullable = false, length = 10)
    private ClassCode classCode;

    @Column(name = "period_no", nullable = false)
    private int periodNo;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 확정 소정훈련일수 */
    @Column(name = "required_days", nullable = false)
    private int requiredDays;

    protected UnitPeriod() {
    }

    public String getId() {
        return id;
    }

    public ClassCode getClassCode() {
        return classCode;
    }

    public int getPeriodNo() {
        return periodNo;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getRequiredDays() {
        return requiredDays;
    }
}
package kr.it.acfourd.knda_check.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import kr.it.acfourd.knda_check.common.ClassCode;

@Entity
@Table(name = "training_day")
public class TrainingDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_code", nullable = false, length = 10)
    private ClassCode classCode;

    @Column(name = "training_date", nullable = false)
    private LocalDate trainingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_period_id", nullable = false)
    private UnitPeriod unitPeriod;

    /** DB 컬럼은 is_training_day. 비훈련일(주말·공휴일)도 행으로 존재한다. */
    @Column(name = "is_training_day", nullable = false)
    private boolean trainingDay;

    protected TrainingDay() {
    }

    public Long getId() {
        return id;
    }

    public ClassCode getClassCode() {
        return classCode;
    }

    public LocalDate getTrainingDate() {
        return trainingDate;
    }

    public UnitPeriod getUnitPeriod() {
        return unitPeriod;
    }

    public boolean isTrainingDay() {
        return trainingDay;
    }
}
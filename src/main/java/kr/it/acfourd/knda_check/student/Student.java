package kr.it.acfourd.knda_check.student;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import kr.it.acfourd.knda_check.common.BaseTimeEntity;
import kr.it.acfourd.knda_check.common.ClassCode;

@Entity
@Table(name = "student")
public class Student extends BaseTimeEntity {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_code", nullable = false, length = 10)
    private ClassCode classCode;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sick_leave_limit", nullable = false)
    private int sickLeaveLimit;

    @Column(name = "enrollment_status", nullable = false, length = 20)
    private String enrollmentStatus;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    protected Student() {
    }

    public String getId() {
        return id;
    }

    public ClassCode getClassCode() {
        return classCode;
    }

    public String getName() {
        return name;
    }

    public int getSickLeaveLimit() {
        return sickLeaveLimit;
    }

    public String getEnrollmentStatus() {
        return enrollmentStatus;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void markEmailVerified(Instant at) {
        this.emailVerifiedAt = at;
    }
}
package com.suwasarana.api.circle;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circle_responses")
public class CircleResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circle_id", nullable = false)
    private RequestCircle circle;

    @Column(name = "responder_name", length = 150, nullable = false)
    private String responderName;

    @Column(name = "responder_phone", length = 20, nullable = false)
    private String responderPhone;

    @Column(name = "blood_type", length = 3, nullable = false)
    private String bloodType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "responded_at", nullable = false, updatable = false)
    private LocalDateTime respondedAt = LocalDateTime.now();

    public CircleResponse() {}

    public CircleResponse(RequestCircle circle, String responderName, String responderPhone, String bloodType, String notes) {
        this.circle = circle;
        this.responderName = responderName;
        this.responderPhone = responderPhone;
        this.bloodType = bloodType;
        this.notes = notes;
        this.respondedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RequestCircle getCircle() { return circle; }
    public void setCircle(RequestCircle circle) { this.circle = circle; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public String getResponderPhone() { return responderPhone; }
    public void setResponderPhone(String responderPhone) { this.responderPhone = responderPhone; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}

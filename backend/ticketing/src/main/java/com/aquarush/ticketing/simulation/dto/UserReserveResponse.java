package com.aquarush.ticketing.simulation.dto;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserReserveResponse {
    private boolean reserved;
    private String failReason;
    private Integer myPosition;
    private String courseName;
    private Integer totalParticipants;
    private Integer successCount;
    private Integer failCount;
}

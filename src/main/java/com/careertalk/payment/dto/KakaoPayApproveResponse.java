package com.careertalk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KakaoPayApproveResponse {

    private String tid;

    @JsonProperty("partner_order_id")
    private String partnerOrderId;

    @JsonProperty("partner_user_id")
    private String partnerUserId;

    @JsonProperty("item_name")
    private String itemName;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("approved_at")
    private String approvedAt;

    @Getter @Setter
    public static class Amount {
        @JsonProperty("total")
        private Integer total;
    }
}
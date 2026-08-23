package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * What happened on each broker.
 *
 * A fan-out reports PER TARGET rather than succeeding or failing as a whole. One
 * account that already runs this exact configuration must not stop the other four
 * from starting, and the caller needs to be told which one it was - so each
 * account gets its own transaction, its own outcome and, when it failed, the same
 * sentence the single-account endpoint would have returned.
 */
@Schema(name = "StrategyDeploymentResponse",
        description = """
                Per-broker outcome of a fan-out deploy.

                The status is 200 whenever the REQUEST was well-formed, even if every target \
                failed - the failures are data, not transport errors. Render results[], not the \
                status code: a 200 with failed=1 is the normal outcome of re-deploying.""")
public record StrategyDeploymentResponse(

        @Schema(example = "us000000-1111-4222-8333-444444444444")
        UUID userStrategyId,

        @Schema(example = "NIFTY 21/9 both sides")
        String userStrategyName,

        @Schema(description = "The market every broker in this deployment runs on.",
                example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
        UUID symbolId,

        @Schema(example = "NIFTY")
        String symbol,

        @Schema(example = "5m")
        String candleDuration,

        @Schema(description = "The one shared computation all of them feed off.",
                example = "sc000000-1111-4222-8333-444444444444")
        UUID sharedConfigId,

        @Schema(example = "6b1f0c9e2ad4471f9c3e5a70b8d21e4f6a9c0b3d5e7f1a2b3c4d5e6f70819a2b")
        String configHash,

        @Schema(description = "How many accounts were addressed, after broker setups were "
                + "expanded into their accounts.", example = "3")
        int requested,

        @Schema(example = "2")
        int deployed,

        @Schema(example = "1")
        int failed,

        List<Item> results) {

    /** STATUS_ values of {@link Item#status()}. */
    public static final String STATUS_DEPLOYED = "deployed";
    public static final String STATUS_FAILED = "failed";

    /** One account's outcome. */
    @Schema(name = "DeploymentItem", description = "One account's outcome.")
    public record Item(

            @Schema(example = "ta000000-1111-4222-8333-444444444444")
            UUID tradingAccountId,

            @Schema(example = "main")
            String tradingAccountName,

            @Schema(description = "The setup the account hangs off, so a UI can group by broker.",
                    example = "ub000000-1111-4222-8333-444444444444")
            UUID userBrokerId,

            @Schema(example = "My Dhan")
            String brokerLabel,

            @Schema(example = "deployed", allowableValues = {"deployed", "failed"})
            String status,

            @Schema(description = "The created deployment; null when status is failed.")
            StrategySubscriptionResponse subscription,

            @Schema(description = "The displayable reason; null when status is deployed.",
                    example = "Strategy NIFTY 21/9 both sides is already deployed on account "
                            + "hedge (subscriptionId=sub00000-1111-4222-8333-444444444444). "
                            + "Change it with PUT /api/v1/my-subscriptions/sub00000-1111-4222-8333-444444444444.")
            String error) {
    }
}

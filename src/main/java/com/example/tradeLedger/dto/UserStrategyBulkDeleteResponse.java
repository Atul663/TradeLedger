package com.example.tradeLedger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * What happened to each strategy in a bulk delete.
 *
 * A sweep reports PER STRATEGY rather than succeeding or failing as a whole. One
 * strategy that is still deployed on a broker must not stop the other nine from
 * being cleared - a live deployment holds a FK to the row, and cascading through
 * it would silently stop trading. So the deployed ones are SKIPPED, with the same
 * sentence the single-strategy endpoint would have returned, and the caller is
 * told which they were.
 */
@Schema(name = "UserStrategyBulkDeleteResponse",
        description = """
                Per-strategy outcome of deleting the caller's strategies.

                The status is 200 whenever the REQUEST was well-formed, even if every strategy \
                was skipped - a strategy still deployed somewhere is data, not a transport \
                error. Render results[], not the status code.""")
public record UserStrategyBulkDeleteResponse(

        @Schema(description = "How many of the caller's strategies the filters matched.",
                example = "10")
        int requested,

        @Schema(example = "8")
        int deleted,

        @Schema(description = "Still deployed somewhere, so left alone.", example = "2")
        int skipped,

        List<Item> results) {

    /** STATUS_ values of {@link Item#status()}. */
    public static final String STATUS_DELETED = "deleted";
    public static final String STATUS_SKIPPED = "skipped";

    /** One strategy's outcome. */
    @Schema(name = "BulkDeleteItem", description = "One strategy's outcome.")
    public record Item(

            @Schema(example = "us000000-1111-4222-8333-444444444444")
            UUID id,

            @Schema(example = "NIFTY 21/9 both sides")
            String name,

            @Schema(description = "The template it was built from, so a UI can group by it.",
                    example = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d")
            UUID strategyId,

            @Schema(example = "EMA Averaging")
            String strategyName,

            @Schema(example = "deleted", allowableValues = {"deleted", "skipped"})
            String status,

            @Schema(description = "How many accounts still run it; 0 when status is deleted.",
                    example = "3")
            long deployments,

            @Schema(description = "The displayable reason; null when status is deleted.",
                    example = "Strategy NIFTY 21/9 both sides is deployed on 3 account(s). "
                            + "Withdraw those deployments first, or archive it with "
                            + "PUT /api/v1/my-strategies/us000000-1111-4222-8333-444444444444 "
                            + "{\"active\":false}.")
            String error) {
    }
}

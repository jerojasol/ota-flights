# Compare prices fails when the provider increases the price 

## Problem
We have detected a bug when the provider increases the price of a flight.
We are comparing the price against the database, if the proveder increases the price, we don't must to maintain the database prices, is necessary to update the itinerary

compare again with the three providers always

## Acceptance

If the provider that was selected in the itinerary creation increases the price, the itinerary must be updated with the new price

### Plan

Check where the bug occurs, tell me before you fix it, ask me any functional or technical questions you have.

## Closing

Run `/check-arch`. Fix CRITICAL and MAJOR findings. Then `/log-ai-decision` noting anything I corrected, and show me the diff summary before committing.

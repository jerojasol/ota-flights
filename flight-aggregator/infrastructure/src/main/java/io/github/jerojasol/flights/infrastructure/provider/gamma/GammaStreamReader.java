package io.github.jerojasol.flights.infrastructure.provider.gamma;

import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Reads Gamma's export one line at a time, never buffering the body: checks the header, hands every data line to the
 * consumer (blank lines are skipped, never counted), and stops at {@code #END}. The three ways it can fail are
 * deliberately different:
 * <ul>
 *   <li>a wrong header, or data after {@code #END} → {@link ProviderContractViolation} (the body as a whole breaks the
 *       contract; not retried);</li>
 *   <li>the stream ends before {@code #END}, or before the header → {@link TruncatedStream} (the connection was cut;
 *       a transport failure, retried);</li>
 *   <li>an I/O error while reading → {@link IOException}, also a transport failure.</li>
 * </ul>
 */
final class GammaStreamReader {

    static final String HEADER = "ref,price_cents,currency,segments";
    static final String END_MARKER = "#END";

    private GammaStreamReader() {
    }

    static void read(BufferedReader body, Consumer<String> dataLine) throws IOException {
        String header = body.readLine();
        if (header == null) {
            // Cut before even the header: the same transport failure as a cut before #END.
            throw new TruncatedStream();
        }
        if (!header.equals(HEADER)) {
            throw new ProviderContractViolation("Gamma export does not start with the header '" + HEADER + "'");
        }
        String line;
        while ((line = body.readLine()) != null) {
            if (line.equals(END_MARKER)) {
                requireNothingAfterEnd(body);
                return;
            }
            // A blank line is not an itinerary, so it is skipped, not counted (as after #END).
            if (!line.isBlank()) {
                dataLine.accept(line);
            }
        }
        throw new TruncatedStream();
    }

    private static void requireNothingAfterEnd(BufferedReader body) throws IOException {
        String line;
        while ((line = body.readLine()) != null) {
            if (!line.isBlank()) {
                throw new ProviderContractViolation("Gamma export has data after " + END_MARKER);
            }
        }
    }

    /** The export ended without {@code #END}: the stream was cut, so a retry may get the whole export. */
    static final class TruncatedStream extends RuntimeException {

        TruncatedStream() {
            super("Gamma export ended without " + END_MARKER + ": the stream was truncated");
        }
    }
}

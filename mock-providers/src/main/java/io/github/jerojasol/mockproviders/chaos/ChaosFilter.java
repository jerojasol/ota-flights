package io.github.jerojasol.mockproviders.chaos;

import java.io.IOException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Random;

import io.github.jerojasol.mockproviders.alpha.AlphaErrorWriter;
import io.github.jerojasol.mockproviders.beta.BetaFaultWriter;
import io.github.jerojasol.mockproviders.gamma.GammaErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects latency and provider-flavoured 500s. Registered only for the provider paths, so actuator and
 * admin are never affected. Uses its own {@link Random}, separate from the inventory generator's.
 */
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);

    private final ChaosState state;
    private final Random random;
    private final AlphaErrorWriter alphaErrors;
    private final BetaFaultWriter betaFaults;
    private final GammaErrorWriter gammaErrors;

    public ChaosFilter(ChaosState state, Random random, AlphaErrorWriter alphaErrors, BetaFaultWriter betaFaults,
                       GammaErrorWriter gammaErrors) {
        this.state = state;
        this.random = random;
        this.alphaErrors = alphaErrors;
        this.betaFaults = betaFaults;
        this.gammaErrors = gammaErrors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ChaosConfig config = state.get();
        String provider = providerOf(request);
        if (!config.enabled() || provider == null) {
            chain.doFilter(request, response);
            return;
        }
        long latencyMs = latencyMillis(config);
        boolean fail = random.nextDouble() < config.errorRate();
        sleep(latencyMs);
        if (fail) {
            String ref = HexFormat.of().toHexDigits(random.nextInt(0x1000000)).substring(2);
            switch (provider) {
                case "alpha" -> alphaErrors.writeInternalError(response, ref);
                case "beta" -> betaFaults.writeSystemError(response, ref);
                default -> gammaErrors.writeExportFailed(response, ref);
            }
            log.info("provider={} latency_ms={} outcome=ERROR", provider, latencyMs);
            return;
        }
        log.info("provider={} latency_ms={} outcome=OK", provider, latencyMs);
        chain.doFilter(request, response);
    }

    private long latencyMillis(ChaosConfig config) {
        long min = config.latencyMin().toMillis();
        long max = config.latencyMax().toMillis();
        return min == max ? min : min + random.nextLong(max - min + 1);
    }

    private static void sleep(long millis) throws ServletException {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException("Interrupted while injecting latency", e);
        }
    }

    static String providerOf(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String provider : new String[] {"alpha", "beta", "gamma"}) {
            if (path.startsWith("/" + provider + "/")) {
                return provider;
            }
        }
        return null;
    }
}

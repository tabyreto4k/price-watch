package ru.pricewatch.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.pricewatch.source.SourceProperties;

@Configuration
@EnableConfigurationProperties(SourceProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient sourceWebClient(WebClient.Builder builder, SourceProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int)
                        properties.connectTimeout().toMillis())
                .responseTimeout(properties.readTimeout());

        return builder.clientConnector(new ReactorClientHttpConnector(httpClient))
                // Честный User-Agent с контактом: чужой сайт должен понимать, кто к нему ходит.
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }
}

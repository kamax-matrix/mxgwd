package io.kamax.matrix.gw.spring.service;

import io.kamax.matrix.gw.model.MatrixGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatrixGatewayService {

    @Bean
    public MatrixGateway getGateway() {
        return new MatrixGateway();
    }

}

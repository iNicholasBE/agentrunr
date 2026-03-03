package io.agentrunr.setup;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SetupWebConfig implements WebMvcConfigurer {

    private final SetupInterceptor setupInterceptor;

    public SetupWebConfig(SetupInterceptor setupInterceptor) {
        this.setupInterceptor = setupInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(setupInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/setup").setViewName("forward:/setup.html");
    }
}

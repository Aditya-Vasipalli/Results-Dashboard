package edu.vit.results.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app from classpath:/static and falls back to index.html so
 * client-side routes like /results/5 survive a page refresh. Unknown /api paths still 404.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.startsWith("api/")) return null;
                        if (!path.isEmpty()) {
                            Resource file = location.createRelative(path);
                            if (file.exists() && file.isReadable() && path.contains(".")) return file;
                            if (path.contains(".")) return null; // a missing asset should 404, not return HTML
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}

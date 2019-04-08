package run.halo.app.listener;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.TemplateModelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;
import run.halo.app.config.properties.HaloProperties;
import run.halo.app.model.entity.User;
import run.halo.app.model.params.UserParam;
import run.halo.app.model.properties.BlogProperties;
import run.halo.app.model.properties.PrimaryProperties;
import run.halo.app.model.support.HaloConst;
import run.halo.app.model.support.Theme;
import run.halo.app.service.OptionService;
import run.halo.app.service.ThemeService;
import run.halo.app.service.UserService;
import run.halo.app.utils.HaloUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The method executed after the application is started.
 *
 * @author : RYAN0UP
 * @date : 2018/12/5
 */
@Slf4j
@Configuration
public class StartedListener implements ApplicationListener<ApplicationStartedEvent> {

    @Autowired
    private freemarker.template.Configuration configuration;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private HaloProperties haloProperties;

    @Autowired
    private OptionService optionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ThemeService themeService;

    @Autowired
    private UserService userService;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        // save halo version to database
        this.cacheThemes();
        this.cacheOwo();
        this.cacheActiveTheme();
        this.printStartInfo();
        this.initThemes();

        // Init user in development environment
        if (!haloProperties.getProductionEnv()) {
            initAnTestUserIfAbsent();
        }
    }

    /**
     * Initialize an test user if absent
     */
    private void initAnTestUserIfAbsent() {
        // Create an user if absent
        List<User> users = userService.listAll();

        if (users.isEmpty()) {
            UserParam userParam = new UserParam();
            userParam.setUsername("test");
            userParam.setNickname("developer");
            userParam.setEmail("test@test.com");

            log.debug("Initializing a test user: [{}]", userParam);

            User testUser = userService.createBy(userParam, "opentest");

            log.debug("Initialized a test user: [{}]", testUser);
        }
    }

    /**
     * Cache themes to map
     */
    private void cacheThemes() {
        final List<Theme> themes = themeService.getThemes();
        if (null != themes) {
            HaloConst.THEMES = themes;
        }
    }

    /**
     * Get active theme
     */
    private void cacheActiveTheme() {
        try {
            configuration.setSharedVariable("themeName", optionService.getTheme());
        } catch (TemplateModelException e) {
            log.error("", e);
        }
    }

    private void printStartInfo() {
        String blogUrl = getBaseUrl();

        log.info("Halo started at         {}", blogUrl);
        // TODO admin may be changeable
        log.info("Halo admin started at   {}/admin", blogUrl);
        if (!haloProperties.getDocDisabled()) {
            log.debug("Halo doc was enable at  {}/swagger-ui.html", blogUrl);
        }
    }

    /**
     * Gets blog url.
     *
     * @return blog url (If blog url isn't present, current machine IP address will be default)
     */
    private String getBaseUrl() {
        // Get server port
        String serverPort = applicationContext.getEnvironment().getProperty("server.port", "8080");

        String blogUrl = optionService.getByPropertyOfNullable(BlogProperties.BLOG_URL);

        if (StrUtil.isNotBlank(blogUrl)) {
            blogUrl = StrUtil.removeSuffix(blogUrl, "/");
        } else {
            blogUrl = String.format("http://%s:%s", HaloUtils.getMachineIP(), serverPort);
        }

        return blogUrl;
    }

    /**
     * Cache Owo
     */
    private void cacheOwo() {
        try {
            // The Map is LinkedHashMap
            @SuppressWarnings("unchecked")
            Map<String, String> owoMap = objectMapper.readValue(ResourceUtils.getURL("classpath:static/halo-common/OwO/OwO.path.json"), Map.class);

            HaloConst.OWO_MAP = Collections.unmodifiableMap(owoMap);
        } catch (IOException e) {
            log.error("Failed to read owo json", e);
            // TODO Consider to throw an exception
        }
    }

    /**
     * Init internal themes
     */
    private void initThemes() {
        // Whether the blog has initialized
        Boolean isInstalled = optionService.getByPropertyOrDefault(PrimaryProperties.IS_INSTALLED, Boolean.class, false);
        try {
            if (isInstalled) {
                // Skip
                return;
            }

            File internalThemePath = new File(ResourceUtils.getURL(ResourceUtils.CLASSPATH_URL_PREFIX).getPath(), "templates/themes");
            File[] internalThemes = internalThemePath.listFiles();
            if (null != internalThemes) {
                for (File theme : internalThemes) {
                    FileUtil.copy(theme, themeService.getThemeBasePath(), true);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Init internal theme to user path error", e);
        }
    }
}
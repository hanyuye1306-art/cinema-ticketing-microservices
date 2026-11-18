package com.gouyan.framework.config;

import com.gouyan.framework.shiro.JwtFilter;
import com.gouyan.framework.shiro.realms.CustomerRealm;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置shiro安全框架
 */
@Configuration
public class ShiroConfig {
    // Services are injected by concrete type throughout the legacy controllers/realm.
    // Keep transaction-advised services class-based instead of Shiro's default JDK proxies.
    @Bean
    @org.springframework.context.annotation.DependsOn("lifecycleBeanPostProcessor")
    public static org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator creator =
                new org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    //创建shiroFilter
    @Bean("shiroFilter")
    public ShiroFilterFactoryBean getShiroFilterFactoryBean(DefaultWebSecurityManager defaultWebSecurityManager){
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();

        //给shiroFilter设置安全管理器
        shiroFilterFactoryBean.setSecurityManager(defaultWebSecurityManager);

        //配置受限资源
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/sysUser/login", "anon");
        map.put("/sysUser/register", "anon");
        map.put("/sysUser/password/code", "anon");
        map.put("/sysUser/password/reset", "anon");
        map.put("/images/**", "anon");//放行图片查询
        map.put("/captcha/**", "anon");//放行验证码请求
        map.put("/**", "jwt");//请求这个资源需要认证与授权

        // 添加自己的过滤器并且取名为jwt
        Map<String, Filter> filterMap = new HashMap<String, Filter>(1);
        filterMap.put("jwt", new JwtFilter());
        shiroFilterFactoryBean.setFilters(filterMap);

        //设置认证规则
        shiroFilterFactoryBean.setFilterChainDefinitionMap(map);
        return shiroFilterFactoryBean;
    }

    //创建安全管理器
    @Bean
    public DefaultWebSecurityManager getDefaultWebSecurityManager(Realm realm){
        DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager();

        //给安全管理器设置realm
        defaultWebSecurityManager.setRealm(realm);

        //关闭shiro自带的session，使得不保存登录状态，每次使用token进行验证
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator defaultSessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        defaultSessionStorageEvaluator.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(defaultSessionStorageEvaluator);
        defaultWebSecurityManager.setSubjectDAO(subjectDAO);

        return defaultWebSecurityManager;
    }

    //创建自定义realm
    @Bean(name = "realm")
    public Realm getRealm(){
        CustomerRealm realm = new CustomerRealm();
        return realm;
    }

}

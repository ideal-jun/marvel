package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.exception.BusinessException;
import com.marvel.framework.config.CacheConfig;
import com.marvel.module.system.entity.SysConfig;
import com.marvel.module.system.mapper.SysConfigMapper;
import com.marvel.module.system.service.SysConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统参数业务实现：configKey 全局唯一校验。
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public List<SysConfig> listConfigs(String configName, String configKey, String enabled) {
        // enabled 参数预留（当前 sys_config 无状态列），仅按名称/键名过滤
        return list(new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(configName), SysConfig::getConfigName, configName)
                .like(StringUtils.hasText(configKey), SysConfig::getConfigKey, configKey)
                .orderByAsc(SysConfig::getConfigId));
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_CONFIG, key = "#configKey")
    public SysConfig getByKey(String configKey) {
        return getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, configKey));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_CONFIG, allEntries = true)
    public void createConfig(SysConfig config) {
        checkKeyUnique(config.getConfigKey(), null);
        config.setConfigId(null);
        this.save(config);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_CONFIG, allEntries = true)
    public void updateConfig(SysConfig config) {
        checkKeyUnique(config.getConfigKey(), config.getConfigId());
        this.updateById(config);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_CONFIG, allEntries = true)
    public void deleteConfigs(List<Long> configIds) {
        if (configIds != null && !configIds.isEmpty()) {
            this.removeByIds(configIds);
        }
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        if (!StringUtils.hasText(configKey)) {
            throw new BusinessException("参数键名不能为空");
        }
        SysConfig existing = getByKey(configKey);
        if (existing != null && !existing.getConfigId().equals(excludeId)) {
            throw new BusinessException("参数键名已存在");
        }
    }
}

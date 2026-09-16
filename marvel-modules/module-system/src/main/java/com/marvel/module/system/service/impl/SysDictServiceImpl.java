package com.marvel.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.marvel.common.constant.Constants;
import com.marvel.common.exception.BusinessException;
import com.marvel.framework.config.CacheConfig;
import com.marvel.module.system.entity.SysDictData;
import com.marvel.module.system.entity.SysDictType;
import com.marvel.module.system.mapper.SysDictDataMapper;
import com.marvel.module.system.mapper.SysDictTypeMapper;
import com.marvel.module.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典管理业务实现。
 *
 * <p>关键规则：
 * <ul>
 *   <li>dict_type 键全局唯一，类型重命名时需同步更新其下所有字典数据的 dict_type；</li>
 *   <li>删除类型时级联删除其字典数据（事务内）；</li>
 *   <li>已有启用数据关联的类型不允许停用，避免前端下拉突然无数据。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType> implements SysDictService {

    private final SysDictDataMapper dictDataMapper;

    @Override
    public List<SysDictType> listTypes(String dictName, String dictType, String status) {
        return list(new LambdaQueryWrapper<SysDictType>()
                .like(StringUtils.hasText(dictName), SysDictType::getDictName, dictName)
                .like(StringUtils.hasText(dictType), SysDictType::getDictType, dictType)
                .eq(StringUtils.hasText(status), SysDictType::getStatus, status)
                .orderByAsc(SysDictType::getDictId));
    }

    @Override
    @Transactional
    public void createType(SysDictType dictType) {
        checkDictTypeUnique(dictType.getDictType(), null);
        dictType.setDictId(null);
        this.save(dictType);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DICT_DATA, allEntries = true)
    public void updateType(SysDictType dictType) {
        SysDictType db = getById(dictType.getDictId());
        if (db == null) {
            throw new BusinessException("字典类型不存在");
        }
        checkDictTypeUnique(dictType.getDictType(), dictType.getDictId());
        // 类型键变更时同步字典数据，保持引用一致
        if (!db.getDictType().equals(dictType.getDictType())) {
            SysDictData update = new SysDictData();
            update.setDictType(dictType.getDictType());
            dictDataMapper.update(update, new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType, db.getDictType()));
        }
        this.updateById(dictType);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DICT_DATA, allEntries = true)
    public void deleteType(Long dictId) {
        SysDictType type = getById(dictId);
        if (type == null) {
            return;
        }
        // 级联删除字典数据
        dictDataMapper.delete(new LambdaQueryWrapper<SysDictData>().eq(SysDictData::getDictType, type.getDictType()));
        this.removeById(dictId);
    }

    @Override
    public List<SysDictData> listData(String dictType, String keyword) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(StringUtils.hasText(dictType), SysDictData::getDictType, dictType)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysDictData::getDictLabel, keyword)
                        .or()
                        .like(SysDictData::getDictValue, keyword))
                .orderByAsc(SysDictData::getOrderNum));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DICT_DATA, allEntries = true)
    public void createData(SysDictData dictData) {
        checkDataValueUnique(dictData, null);
        dictData.setDictCode(null);
        dictDataMapper.insert(dictData);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DICT_DATA, allEntries = true)
    public void updateData(SysDictData dictData) {
        checkDataValueUnique(dictData, dictData.getDictCode());
        dictDataMapper.updateById(dictData);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DICT_DATA, allEntries = true)
    public void deleteData(List<Long> dictCodes) {
        if (dictCodes != null && !dictCodes.isEmpty()) {
            dictDataMapper.deleteByIds(dictCodes);
        }
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_DICT_DATA, key = "#dictType")
    public List<SysDictData> listEnabledData(String dictType) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysDictData::getOrderNum));
    }

    /** 同一类型内 dictValue 唯一 */
    private void checkDataValueUnique(SysDictData dictData, Long excludeCode) {
        SysDictData existing = dictDataMapper.selectOne(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictData.getDictType())
                .eq(SysDictData::getDictValue, dictData.getDictValue()));
        if (existing != null && !existing.getDictCode().equals(excludeCode)) {
            throw new BusinessException("当前字典类型下键值已存在");
        }
    }

    private void checkDictTypeUnique(String dictType, Long excludeId) {
        SysDictType existing = getOne(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getDictType, dictType));
        if (existing != null && !existing.getDictId().equals(excludeId)) {
            throw new BusinessException("字典类型键已存在");
        }
    }
}

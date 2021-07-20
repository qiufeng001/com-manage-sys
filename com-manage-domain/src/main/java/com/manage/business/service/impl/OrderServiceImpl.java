package com.manage.business.service.impl;

import com.manage.business.domain.Order;
import com.manage.business.domain.OrderDetail;
import com.manage.business.domain.TDiscount;
import com.manage.business.domain.TGoods;
import com.manage.business.mapper.OrderMapper;
import com.manage.business.mapper.TDiscountMapper;
import com.manage.business.mapper.TGoodsMapper;
import com.manage.common.core.constant.ErrConstants;
import com.manage.common.core.core.service.impl.BaseServiceImpl;
import com.manage.common.core.utils.SecurityUtils;
import com.manage.common.core.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.manage.common.core.core.mapper.IMapper;
import com.manage.business.service.IOrderService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单Service业务层处理
 *
 * @author zhong.h
 * @date 2021-07-20
 */
@Service
public class OrderServiceImpl extends BaseServiceImpl<Order, Long> implements IOrderService {

    @Autowired
    private OrderMapper mapper;

    @Autowired
    private TGoodsMapper goodsMapper;

    @Autowired
    private TDiscountMapper discountMapper;

    @Override
    protected IMapper getMapper() {
        return mapper;
    }

    /**
     * 新增
     *
     * @param entity
     * @return 结果
     */
    @Transactional
    public Map<String, Object> addOrder(Order entity) {
        Map<String, Object> result = new HashMap<>();
        synchronized (this) {
            // 查看库存是否满足
            List<OrderDetail> details = entity.getOrderDetails();
            Map<Long, TGoods> goodsMap = new HashMap<>();
            this.genGoodsMap(details, goodsMap, result);
            if((Integer) result.get("code") == 500) {
                return result;
            }

            this.genOrder(entity, details, goodsMap, result);
        }

        return result;
    }

    private void genGoodsMap(List<OrderDetail> details,
                               Map<Long, TGoods> goodsMap,
                               Map<String, Object> result) {
        StringBuilder errMsg = new StringBuilder();
        for(OrderDetail detail : details) {
            TGoods goods = goodsMapper.selectById(detail.getGoodsId());
            // 验证订单数量和库存是否一致
            if(detail.getGoodsNum() >= goods.getNumber()) {
                // 此订单超过了订单总数
                errMsg.append(goods.getName()).append(",");
            }
            goodsMap.put(detail.getGoodsId(), goods);
        }
        // 只要有一个不对则订单无效
        if(StringUtils.isNotEmpty(errMsg.toString())) {
            result.put("code", "500");
            String err = errMsg.toString();
            result.put("errMsg", err.substring(0,err.length() - 1));
        }
    }

    /**
     * 数据组合
     *
     * @param entity
     * @param details
     * @param goodsMap
     * @param result
     */
    private void genOrder(Order entity,
                          List<OrderDetail> details,
                          Map<Long, TGoods> goodsMap,
                          Map<String, Object> result) {
        String orderNo = super.generateId();
        try {
            // 实际总额
            Float total_amount = 0F;
            for(OrderDetail detail : details) {
                TGoods goods = goodsMap.get(detail.getGoodsId());
                // 减库存
                goods.setNumber(goods.getNumber() - detail.getGoodsNum());
                // 增加总数
                goods.setTotal(goods.getTotal() + detail.getGoodsNum());
                goodsMapper.update(goods);
                // 实际金额汇总
                total_amount += goods.getUnitPrice() * detail.getGoodsNum();
                // 生成订单明细
                detail.setOrderNo(orderNo);
                mapper.insertDetail(detail);
            }
            // 生成订单
            TDiscount discount = discountMapper.selectById(entity.getDiscountId());
            // 实收金额
            Float paidAmount = total_amount * discount.getDiscountRate();

            entity.setOrderNo(orderNo);
            entity.setDiscountId(discount.getId());
            entity.setPayee(SecurityUtils.getUsername());
            entity.setPaidInAmount(paidAmount);
            entity.setTotalAmount(total_amount);
            entity.setShopId(SecurityUtils.getLoginUser().getUser().getShop().getId());
            // 保存订单
            if(entity.getId() == null) {
                initEntry(entity);
                mapper.insert(entity);
            }else {
                entity.setUpdateBy(SecurityUtils.getUsername());
                entity.setUpdateTime(new Date());
                mapper.update(entity);
            }
            result.put("code", 200);
        }catch (Exception e) {
            result.put("code", 500);
            result.put("errMsg", ErrConstants.SYSTEM_ERR);
        }
    }

    /**
     * 修改
     *
     * @param entity
     * @return 结果
     */
    @Transactional
    public Map<String, Object> updateOrder(Order entity) {
        Map<String, Object> result = new HashMap<>();
        synchronized (this) {
            // 查看库存是否满足
            List<OrderDetail> details = entity.getOrderDetails();
            Map<Long, TGoods> goodsMap = new HashMap<>();
            this.genGoodsMap(details, goodsMap, result);
            if((Integer) result.get("code") == 500) {
                return result;
            }
            // 验证成功后，先删除所有订单明细，重新生成
            mapper.deleteDetailByOrderNo(entity.getOrderNo());
            this.genOrder(entity, details, goodsMap, result);
        }
        return result;
    }
}

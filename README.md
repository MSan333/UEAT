🏫 CampusEats 是一个为高校师生量身定制的周边餐饮服务平台，集成商户点评、限时秒杀、社交互动等功能。能够接入校园周边商家，提供独家优惠券秒杀活动

业务场景亮点：
✅ 校园餐饮优惠聚合平台
✅ 实时更新的商家排行榜（口味/性价比/配送速度）
✅ 限量秒杀活动
✅ 好友关注系统与动态点评消息推送

## 🛠️ 技术栈全景
- **核心框架**: SpringBoot + MyBatis-Plus + Redisson
- **数据存储**: MySQL + Redis
- **并发控制**: Redis分布式锁 + Lua脚本 + 乐观锁
- **异步处理**: Redis Stream消息队列
- **特色功能**: 
  - 实时排行榜（ZSet）
  - 社交关系链（Set）
  - 点赞系统（List+ZSet）
  - Feed流推送（推模式）
    
## 🚀 核心功能与实现
|功能模块	|技术方案|
|----|----|
|商户展示|	多级缓存（Redis+MySQL） + 随机过期时间	|
|秒杀系统|	Redis预检库存 + Lua原子操作 + Stream异步下单	|
|社交互动|	Redis Set（共同关注） + ZSet（点赞排行） + List（动态Feed流）	|
|安全控制|	Redis分布式Session + 拦截器自动续期	|
|地理搜索|	Redis GEO + Hash存储商户坐标	|
|签到系统|	BitMap连续签到统计	|

## 🚀 快速开始
client包下为客户端启动类
ueat包下为服务端启动类

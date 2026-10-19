# HMDP-Plus API 接口文档

> 本文依据当前 Spring Boot Controller、DTO、实体类、拦截器、业务实现和异常处理代码静态整理。后端基准地址为 `http://127.0.0.1:8085`。后端接口没有统一的 `/api` 前缀；`/api` 仅是 Vue 开发服务器的代理前缀。

## 1. 接口规范

### 1.1 请求约定

- JSON 请求体接口应设置 `Content-Type: application/json`；文件上传接口使用 `multipart/form-data`。
- 需要登录的接口应携带原始登录令牌，不需要添加 `Bearer` 前缀。

```http
authorization: <登录接口返回的 token>
```

- HTTP 请求头名称不区分大小写，因此前端使用的 `Authorization` 与后端读取的 `authorization` 等效。
- 日期时间格式为 `yyyy-MM-dd HH:mm:ss`，服务端时区为 GMT+8。
- Jackson 开启了数字字符串化，Java 数值字段在实际 JSON 中通常表现为字符串；尤其不能把订单 ID、用户 ID 等长整型直接按 JavaScript `Number` 处理。文档表格仍使用 Java 逻辑类型描述字段。

### 1.2 登录校验范围

当前拦截器明确放行以下路径：

| 路径 | 登录要求 | 说明 |
| --- | --- | --- |
| `/user/code`、`/user/login` | 不需要 | 验证码和登录入口。 |
| `/shop/**` | 不需要 | 包括查询、新增和更新商铺。 |
| `/shop-type/**` | 不需要 | 商铺类型查询。 |
| `/voucher/**` | 拦截器不校验 | 部分订阅接口的业务实现仍会读取当前用户，调用时实际需要有效令牌。 |
| `/upload/**` | 不需要 | 图片上传和删除。 |
| `/blog/hot` | 不需要 | 热门博客列表；携带有效令牌时会补充当前用户点赞状态。 |
| 其他路径 | 需要 | 无有效登录态时返回 HTTP 401。 |

### 1.3 通用响应格式

成功响应：

```json
{
  "success": true,
  "data": {}
}
```

业务失败响应：

```json
{
  "success": false,
  "errorMsg": "错误原因"
}
```

参数校验失败响应：

```json
{
  "success": false,
  "data": [
    {
      "argumentName": "voucherId",
      "message": "不能为null"
    }
  ]
}
```

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `success` | `Boolean` | `true` 表示业务成功，`false` 表示业务失败。 |
| `errorMsg` | `String` | 业务异常或系统异常说明；成功时通常省略。 |
| `data` | `Object` | 业务结果，也可能是参数错误数组；无数据时通常省略。 |
| `total` | `Long` | 预留的总数属性；当前 Controller 返回结果未设置该字段。 |

> 配置启用了非空字段序列化，因此值为 `null` 的字段不会出现在 JSON 中。除登录拦截返回 HTTP 401 外，大部分业务异常由统一异常处理器包装成 `Result`，HTTP 状态通常仍为 200，客户端需要检查 `success`。

## 2. 用户与认证接口

### 2.1 接口列表

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/user/code` | 否 | Query：`phone` | `String` | 生成并返回 6 位验证码。 |
| `POST` | `/user/login` | 否 | `LoginFormDTO` | `String` | 验证码登录并返回令牌。 |
| `POST` | `/user/logout` | 是 | 无 | 无 | 当前固定返回“功能未完成”。 |
| `GET` | `/user/me` | 是 | 无 | `UserDTO` | 查询当前登录用户。 |
| `GET` | `/user/info/{id}` | 是 | Path：`id` | `UserInfo` | 按用户信息表主键查询详情。 |
| `POST` | `/user/level/update` | 是 | Query：`newLevel` | 无 | 更新当前用户会员等级。 |
| `GET` | `/user/{id}` | 是 | Path：`id` | `UserDTO` | 按用户 ID 查询公开信息。 |
| `POST` | `/user/sign` | 是 | 无 | 无 | 记录当天签到。 |
| `GET` | `/user/sign/count` | 是 | 无 | `Integer` | 查询截至当天的连续签到天数。 |

### 2.2 获取验证码

```http
POST /user/code?phone=13800138000
```

| 参数 | 位置 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- | --- |
| `phone` | Query | `String` | 是 | 中国大陆手机号，需通过项目正则校验。 |

当前实现会把验证码写入 Redis，过期时间为 2 分钟，并直接通过 `data` 返回验证码。

### 2.3 验证码登录

```http
POST /user/login
Content-Type: application/json
```

请求体：

```json
{
  "phone": "13800138000",
  "code": "123456"
}
```

| 请求字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `phone` | `String` | 是 | 登录手机号。 |
| `code` | `String` | 是 | `/user/code` 生成并写入 Redis 的验证码。 |
| `password` | `String` | 否 | DTO 中存在，但当前登录实现不读取该字段。 |

成功响应：

```json
{
  "success": true,
  "data": "0f7d947671e44f6496b1ed3d36b25a21"
}
```

调用链：手机号与验证码 -> `/user/login` -> 校验 Redis 验证码 -> 查询或自动创建用户 -> 生成令牌 -> 用户摘要写入 Redis -> 返回令牌。

### 2.4 用户响应字段

`UserDTO`：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | `Long` | 用户 ID，JSON 中通常为字符串。 |
| `nickName` | `String` | 用户昵称。 |
| `icon` | `String` | 用户头像。 |

`UserInfo`：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | `Long` | 用户信息表主键。 |
| `userId` | `Long` | 关联用户 ID。 |
| `city` | `String` | 城市。 |
| `introduce` | `String` | 个人介绍。 |
| `fans` | `Integer` | 粉丝数。 |
| `followee` | `Integer` | 关注数。 |
| `gender` | `Boolean` | `false` 表示男，`true` 表示女。 |
| `birthday` | `LocalDate` | 生日。 |
| `credits` | `Integer` | 积分。 |
| `level` | `Integer` | 会员等级。实体注释为 0～9；等级更新接口当前仅校验大于 0。 |

## 3. 商铺与商铺类型接口

### 3.1 商铺接口

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/shop/{id}` | 否 | Path：`id` | `Shop` | 按 ID 查询商铺。 |
| `POST` | `/shop` | 否 | `Shop` | `Long` | 新增商铺并返回商铺 ID。 |
| `PUT` | `/shop` | 否 | `Shop` | 无 | 按 `id` 更新商铺并删除缓存。 |
| `GET` | `/shop/of/type` | 否 | Query：`typeId`、`current`、`x`、`y` | `Shop[]` | 按类型分页查询商铺。 |
| `GET` | `/shop/of/name` | 否 | Query：`name`、`current` | `Shop[]` | 按名称分页搜索商铺。 |

`/shop/of/type` 参数：

| 参数 | 类型 | 必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `typeId` | `Integer` | 是 | 无 | 商铺类型 ID。 |
| `current` | `Integer` | 否 | `1` | 当前页，每页 5 条。 |
| `x` | `Double` | 否 | 无 | 经度。当前服务实现会主动置空，暂不执行附近排序。 |
| `y` | `Double` | 否 | 无 | 纬度。当前服务实现会主动置空，暂不执行附近排序。 |

`/shop/of/name` 的 `name` 可选，`current` 默认 `1`，每页最多 10 条。

### 3.2 商铺请求与响应字段

| 字段 | 类型 | 新增/更新说明 | 含义 |
| --- | --- | --- | --- |
| `id` | `Long` | 更新时必需 | 商铺 ID。 |
| `name` | `String` | 可传 | 商铺名称。 |
| `typeId` | `Long` | 可传 | 商铺类型 ID。 |
| `images` | `String` | 可传 | 图片路径，多个路径以逗号分隔。 |
| `area` | `String` | 可传 | 商圈。 |
| `address` | `String` | 可传 | 地址。 |
| `x` | `Double` | 可传 | 经度。 |
| `y` | `Double` | 可传 | 纬度。 |
| `avgPrice` | `Long` | 可传 | 人均价格。 |
| `sold` | `Integer` | 可传 | 销量。 |
| `comments` | `Integer` | 可传 | 评论数。 |
| `score` | `Integer` | 可传 | 评分乘以 10 后的整数。 |
| `openHours` | `String` | 可传 | 营业时间。 |
| `distance` | `Double` | 仅响应 | 距离；当前附近查询逻辑未启用。 |
| `createTime`、`updateTime` | `LocalDateTime` | 通常由服务端维护 | 创建和更新时间。 |

### 3.3 商铺类型接口

```http
GET /shop-type/list
```

无需登录，按 `sort` 升序返回 `ShopType[]`。主要字段为 `id`、`name`、`icon`、`sort`，创建和更新时间不会序列化。

## 4. 博客与关注接口

### 4.1 博客接口

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/blog` | 是 | `Blog` | `Long` | 发布博客并推送到粉丝收件箱。 |
| `PUT` | `/blog/like/{id}` | 是 | Path：`id` | 无 | 点赞或取消点赞。 |
| `GET` | `/blog/of/me` | 是 | Query：`current` | `Blog[]` | 查询当前用户博客，每页最多 10 条。 |
| `GET` | `/blog/hot` | 否 | Query：`current` | `Blog[]` | 按点赞数倒序查询热门博客。 |
| `GET` | `/blog/{id}` | 是 | Path：`id` | `Blog` | 查询博客详情。 |
| `GET` | `/blog/likes/{id}` | 是 | Path：`id` | `UserDTO[]` | 查询最早点赞的前 5 名用户。 |
| `GET` | `/blog/of/user` | 是 | Query：`id`、`current` | `Blog[]` | 查询指定用户博客。 |
| `GET` | `/blog/of/follow` | 是 | Query：`lastId`、`offset` | `ScrollResult` | 滚动分页查询关注流。 |

分页参数 `current` 默认值均为 `1`。关注流的 `lastId` 为上一页最小时间戳，`offset` 为同一时间戳偏移量，默认 `0`。

### 4.2 博客字段

| 字段 | 类型 | 请求/响应说明 | 含义 |
| --- | --- | --- | --- |
| `id` | `Long` | 服务端生成 | 博客 ID。 |
| `shopId` | `Long` | 发布时可传 | 关联商铺 ID。 |
| `userId` | `Long` | 服务端覆盖 | 发布用户 ID。 |
| `title` | `String` | 发布时可传 | 标题。 |
| `images` | `String` | 发布时可传 | 图片路径，最多 9 张并以逗号分隔。 |
| `content` | `String` | 发布时可传 | 正文。 |
| `liked` | `Integer` | 主要用于响应 | 点赞数。 |
| `comments` | `Integer` | 主要用于响应 | 评论数。 |
| `icon`、`name` | `String` | 仅响应补充 | 作者头像和昵称。 |
| `isLike` | `Boolean` | 仅响应补充 | 当前登录用户是否已点赞。 |
| `createTime`、`updateTime` | `LocalDateTime` | 主要用于响应 | 创建和更新时间。 |

`ScrollResult` 返回字段为 `list`、`minTime` 和 `offset`，客户端应把后两项用于下一页请求。

### 4.3 关注接口

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `PUT` | `/follow/{id}/{isFollow}` | 是 | Path：用户 ID、关注标记 | 无 | `isFollow=true` 时关注，否则取消关注。 |
| `GET` | `/follow/or/not/{id}` | 是 | Path：用户 ID | `Boolean` | 判断当前用户是否已关注指定用户。 |
| `GET` | `/follow/common/{id}` | 是 | Path：用户 ID | `UserDTO[]` | 查询当前用户与指定用户的共同关注。 |

> `BlogCommentsController` 当前只有 `/blog-comments` 类级路径，没有可调用的方法级接口。

## 5. 优惠券接口

### 5.1 接口列表

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/voucher/get` | 拦截器不校验 | `GetSeckillVoucherDto` | `SeckillVoucherFullModel` | 查询秒杀券完整信息。 |
| `POST` | `/voucher/seckill` | 拦截器不校验 | `SeckillVoucherDto` | `Long` | 创建秒杀券。 |
| `POST` | `/voucher/update/seckill` | 拦截器不校验 | `UpdateSeckillVoucherDto` | 无 | 更新秒杀券基础信息和活动配置。 |
| `POST` | `/voucher/update/seckill/stock` | 拦截器不校验 | `UpdateSeckillVoucherStockDto` | 无 | 修改初始库存并同步剩余库存。 |
| `POST` | `/voucher` | 拦截器不校验 | `VoucherDto` | `Long` | 创建普通优惠券。 |
| `GET` | `/voucher/list/{shopId}` | 拦截器不校验 | Path：`shopId` | `Voucher[]` | 查询商铺优惠券列表。 |
| `POST` | `/voucher/subscribe` | 业务上需要登录 | `VoucherSubscribeDto` | 无 | 订阅到券提醒。 |
| `POST` | `/voucher/unsubscribe` | 业务上需要登录 | `VoucherSubscribeDto` | 无 | 取消到券提醒。 |
| `POST` | `/voucher/get/subscribe/status` | 业务上需要登录 | `VoucherSubscribeDto` | `Integer` | 查询单张券的订阅状态。 |
| `POST` | `/voucher/get/subscribe/status/batch` | 业务上需要登录 | `VoucherSubscribeBatchDto` | `GetSubscribeStatusVo[]` | 批量查询订阅状态。 |
| `POST` | `/voucher/delay/voucher/reminder` | 拦截器不校验 | `DelayVoucherReminderDto` | 无 | 手动投递延迟提醒。 |

### 5.2 普通优惠券请求体

`POST /voucher`：

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `shopId` | `Long` | 是 | 商铺 ID。 |
| `title` | `String` | 是且非空 | 标题。 |
| `subTitle` | `String` | 是且非空 | 副标题。 |
| `rules` | `String` | 是且非空 | 使用规则。 |
| `payValue` | `Long` | 是 | 支付金额。 |
| `actualValue` | `Long` | 是 | 抵扣金额。 |
| `type` | `Integer` | 是 | `0` 普通券，`1` 秒杀券。 |
| `status` | `Integer` | 是 | `1` 上架，`2` 下架，`3` 过期。 |

### 5.3 创建秒杀券请求体

`POST /voucher/seckill` 在普通券字段基础上增加以下字段，其中 `rules` 可选：

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `stock` | `Integer` | 是 | 初始库存。 |
| `beginTime` | `LocalDateTime` | 是 | 开始时间，格式 `yyyy-MM-dd HH:mm:ss`。 |
| `endTime` | `LocalDateTime` | 是 | 结束时间，格式 `yyyy-MM-dd HH:mm:ss`。 |
| `allowedLevels` | `String` | 否 | 允许参与的会员等级，例如 `"1,2,3"`。 |
| `minLevel` | `Integer` | 否 | 最低会员等级。 |

### 5.4 更新秒杀券请求体

`POST /voucher/update/seckill`：

| 字段 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `voucherId` | `Long` | 是 | 优惠券 ID。 |
| `title`、`subTitle`、`rules` | `String` | 否 | 基础展示信息。 |
| `payValue`、`actualValue` | `Long` | 否 | 金额信息。 |
| `type`、`status` | `Integer` | 否 | 券类型和状态。 |
| `beginTime`、`endTime` | `LocalDateTime` | 否 | 活动起止时间。 |
| `allowedLevels` | `String` | 否 | 允许参与的会员等级。 |
| `minLevel` | `Integer` | 否 | 最低会员等级。 |
| `allowedCities` | `String` | 否 | DTO 中存在该字段，但当前服务实现未持久化。 |

`POST /voucher/update/seckill/stock`：

```json
{
  "voucherId": "1001",
  "initStock": 200
}
```

`voucherId` 必填；`initStock` 必填且最小为 `1`。服务会按新旧初始库存差值同步更新数据库剩余库存与 Redis 库存。

### 5.5 查询、订阅和提醒请求体

| DTO | 字段 | 类型 | 必填 | 使用接口 |
| --- | --- | --- | --- | --- |
| `GetSeckillVoucherDto` | `voucherId` | `Long` | 是 | `/voucher/get` |
| `VoucherSubscribeDto` | `voucherId` | `Long` | 是 | 订阅、取消订阅、单个状态查询 |
| `VoucherSubscribeBatchDto` | `voucherIdList` | `Long[]` | 是 | 批量状态查询 |
| `DelayVoucherReminderDto` | `voucherId` | `Long` | 是 | 手动延迟提醒 |
| `DelayVoucherReminderDto` | `delaySeconds` | `Integer` | 是 | 延迟秒数 |

订阅状态含义：

| 状态值 | 含义 |
| --- | --- |
| `0` | 未订阅或已取消订阅。 |
| `1` | 已进入到券提醒队列。 |
| `2` | 自动发券成功并已创建订单。 |

### 5.6 优惠券响应字段

`Voucher` 主要字段：`id`、`shopId`、`title`、`subTitle`、`rules`、`payValue`、`actualValue`、`type`、`status`、`stock`、`beginTime`、`endTime`、`createTime`、`updateTime`。

`SeckillVoucherFullModel` 主要字段：`id`、`voucherId`、`initStock`、`stock`、`allowedLevels`、`minLevel`、`beginTime`、`endTime`、`status`、`shopId`、`createTime`。

## 6. 秒杀订单接口

### 6.1 接口列表

| 方法 | 路径 | 登录 | 请求参数 | `data` 类型 | 功能 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/voucher-order/seckill/token/{id}` | 是 | Path：优惠券 ID | `String` | 获取一次性秒杀访问令牌。 |
| `POST` | `/voucher-order/seckill/{id}` | 是 | Path：优惠券 ID；Query：`accessToken` | `Long` | 提交秒杀，成功受理后返回订单 ID。 |
| `POST` | `/voucher-order/get/seckill/voucher/order-id` | 是 | `GetVoucherOrderDto` | `Long` | 按订单 ID 查询是否已经落单。 |
| `POST` | `/voucher-order/get/seckill/voucher/order-id/by/voucher-id` | 是 | `GetVoucherOrderByVoucherIdDto` | `Long` | 查询当前用户在指定券下的有效订单 ID。 |
| `POST` | `/voucher-order/cancel` | 是 | `CancelVoucherOrderDto` | `Boolean` | 取消订单并回流数据库和 Redis 库存。 |
| `POST` | `/voucher-order/reconciliation/task/all` | 是 | 无 | 无 | 手动触发全部秒杀券对账。 |

### 6.2 秒杀下单流程

第一步，获取一次性访问令牌：

```http
GET /voucher-order/seckill/token/1001
authorization: <登录令牌>
```

第二步，提交秒杀：

```http
POST /voucher-order/seckill/1001?accessToken=<一次性访问令牌>
authorization: <登录令牌>
```

默认配置下访问令牌有效期为 30 秒，校验成功后即被消费。接口还会执行用户/IP 限流、会员等级校验、Redis Lua 原子扣减，并异步投递 Kafka 下单消息。

成功响应中的 `data` 是预生成的订单 ID，不代表 MySQL 已经完成落库；客户端应继续调用订单结果查询接口。

调用链：获取访问令牌 -> 提交秒杀 -> Redis 原子扣减 -> 返回订单 ID -> Kafka 异步创建订单 -> 查询订单落库结果。

### 6.3 订单查询与取消请求体

| 接口 | 请求体 | 字段说明 |
| --- | --- | --- |
| 按订单 ID 查询 | `{"orderId":"1900000000000000000"}` | `orderId` 必填。未落单或不存在时成功响应不包含 `data`。 |
| 按优惠券查询 | `{"voucherId":"1001"}` | `voucherId` 必填，只返回当前用户状态正常的订单。 |
| 取消订单 | `{"voucherId":"1001"}` | `voucherId` 必填，只能取消当前用户状态正常的订单。 |

取消成功后，服务会更新订单状态、记录对账日志、恢复 MySQL 与 Redis 库存、扣减 Top 买家分值，并尝试把释放资格分配给最早订阅且尚未购买的用户。

## 7. 文件与监控接口

### 7.1 博客图片上传

```http
POST /upload/blog
Content-Type: multipart/form-data
```

| 参数 | 位置 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- | --- |
| `file` | Form Data | `MultipartFile` | 是 | 待上传图片。 |

成功后 `data` 返回类似 `/blogs/1/2/uuid.jpg` 的相对路径。文件会写入 `SystemConstants.IMAGE_UPLOAD_DIR` 配置的本地目录。

### 7.2 删除博客图片

```http
GET /upload/blog/delete?name=/blogs/1/2/uuid.jpg
```

`name` 为必填 Query 参数。当前接口使用 `GET` 执行删除操作。

### 7.3 测试指标上报

```http
GET /test/meter
authorization: <登录令牌>
```

Controller 使用 `@RequestMapping`，技术上可接受多种 HTTP 方法；建议使用 `GET`。调用后递增 `test_query_shop` Micrometer Counter，并返回当前计数文本。

## 8. 当前源码边界

- `/user/logout` 尚未实现；`/user/code` 当前会在响应和日志中暴露验证码，只适合开发环境。

- `/shop/**` 和 `/voucher/**` 被登录拦截器整体放行，新增商铺、更新商铺、创建或修改优惠券等接口没有角色权限控制；订阅接口虽然被放行，但实现会读取 `UserHolder`，不携带有效令牌可能进入全局异常处理。

- `/shop/of/type` 当前忽略传入的 `x`、`y`；`UpdateSeckillVoucherDto.allowedCities` 当前未持久化；`/voucher-order/reconciliation/task/all` 是手动运维入口，当前没有细粒度管理员鉴权。

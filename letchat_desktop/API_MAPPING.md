# API接口映射文档+任务
ps: 后端端口为 localhost:5050   ws为localhost:5051

1. 所有http接口都带有/api前缀,所有接口的参数都在Body的form-data中，返回值都在响应体中,响应体格式为：
```json
{
    "status": "success",
    "code": 200,
    "info": "请求成功",
    "data": {}
}
```
其中info为状态描述，data为返回数据

2. 除了注册（注册完成后需要登录）和登录不需要token，其他http接口都需要在请求头中添加token，token在登录成功后返回，格式为：
```json
{
    "data": {
        "token": "123456"
    }
}
```

3. 登录成功后建立websocket连接，token放在请求参数里(?token="xxxxxx")，连接成功后,每隔4s发送一次心跳信息："heart"

4. 登出时要断开ws连接

5. 若websocket连接断开后，提示连接断开并返回登录界面，要求重新登录

6. 登录成功后，会调用`/update/checkVersion` 返回最新版本号，若有更新，会弹出更新界面提示用户是否更新
检查更新接口返回值格式为：
```json
{
    "status": "success",
    "code": 200,
    "info": "请求成功",
    "data": {
        "id": 9,
        "version": "1.2.1",
        "updateList": [
            "修复了一些bug",
            "新增了一些功能"
        ],
        "size": null,
        "fileName": "EasyChatSetup1.2.1.exe",
        "fileType": null,
        "outerLink": "http://www.xxx.com"
    }
}
```
其中version为版本号（若客户端当前版本号小于最新版本号，才弹出更新界面），info为更新信息，outerLink为下载地址

7. 
```
public class UserInfo{
    /**
     * 主键
     */
    private String userId;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 昵称
     */
    private String nickName;
    /**
     * 0:直接加入 1:同意后加好友
     */
    private Integer joinType;
    /**
     * 性别 0:女 1:男
     */
    private Integer sex;
    /**
     * 密码
     */
    private String password;
    /**
     * 个性签名
     */
    private String personalSignature;
    /**
     * 账号状态
     */
    private Integer status;
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;
    /**
     * 地区
     */
    private String areaName;
    /**
     * 地区编号
     */
    private String areaCode;
    /**
     * 最后离开时间
     */
    private Long lastOffTime;

    private Integer onlineType;

    public Integer getOnlineType() {
        if (lastLoginTime != null && lastLoginTime.getTime() >= lastOffTime) {
            return 1;   //在线
        } else {
            return 0;   //离线
        }
    }
}
```


#### 1.  用户相关接口
-  `/userInfo/getUserInfo` - 获取用户信息  后端所需
-  `/userInfo/saveUserInfo` - 保存用户信息  后端所需(UserInfo userInfo,MultipartFile avatarFile, MultipartFile avatarCover)
-  `/userInfo/updatePassword` - 更新密码  后端所需(String password)
-  `/userInfo/logout` - 用户登出  后端所需

#### 2.  聊天相关接口1
-  `/contact/search` - 搜索联系人（包括好友和群聊） (String contactId)
-  `/contact/applyAdd` - 申请添加联系人   (String contactId, String applyInfo)
-  `/contact/loadApply` - 加载申请列表   (Integer pageNo)
-  `/contact/dealWithApply` - 处理申请  (Integer applyId, Integer status) 1:同意,2:拒绝,3:拉黑
-  `/contact/loadContact` - 加载我的联系人（好友和群聊）  (String contactType) U:好友,G:群聊
-  `/contact/getContactInfo` - 获取联系人信息  (String contactId)
-  `/contact/getContactUserInfo` - 获取联系人用户信息  (String contactId)
-  `/contact/delContact` - 删除联系人   (String contactId)
-  `/contact/addContact2BlackList` - 加入黑名单   (String contactId)

#### 3.  账号相关接口
-  `/account/register` - 用户注册（）  (String checkCodeKey,String email,String password,String nickName,String checkCode)
-  `/account/login` - 用户登录  (String checkCodeKey,String email,String password,String checkCode)
-  `/account/getSysSetting` - 获取系统设置
-  `/account/checkCode` - 获取图片验证码(返回图片Base64编码 checkCode,checkCodeKey)

#### 4.  群组相关接口
-  `/group/saveGroup` - 新建/修改群组  (String groupId,String groupName,String groupNotice,Integer joinType,MultipartFile avatarFile,MultipartFile avatarCover)
-  `/group/loadMyGroup` - 加载我的群组（和加载出来的联系人放一个列表）
-  `/group/getGroupInfo` - 获取群组信息（包含群人数）  (String groupId)
-  `/group/getGroupInfo4chat` - 获取聊天群组信息（群里右上角详细按钮查看的，包含成员列表）  (String groupId)
-  `/group/addOrRemoveGroupUser` - 拉人或踢出群聊（只有群主能操作，被拉人或被踢出后，会在群里收到提示）  (String groupId,String selectContacts,Integer opType)  opType 0:拉人 1：踢出
-  `/group/leaveGroup` - 主动退出群聊  (String groupId)
-  `/group/dissolutionGroup` - 群主解散群组（解散后，所有人不能再发消息，并在群里收到提示）  (String groupId)

#### 5.  聊天相关接口2
-  `/chat/sendMessage` - 发送消息  (String contactId,String messageContent,Integer messageType,Long fileSize,String fileName,Integer fileType)  fileType 2:文本消息5:媒体文件（图片或视频）  如果发的时媒体文件，先发送消息，得到返回的messageId，再上传文件
-  `/chat/uploadFile` - 上传文件  (Long messageId,MultipartFile file,MultipartFile coverFile)
-  `/chat/loadChatMessage` - 加载聊天记录
-  `/chat/downloadFile` - 下载文件  (String fileId,Boolean showCover)  fileId其实就是messageId，ws发过来的消息里会有messageId，showCover为是否展示封面，视频要先展示封面；头像如果是点开的大头像：showCover为false，如果是未点开的小头像：showCover为true

#### 6.  检查更新
-  `/update/checkVersion` 后端需要的参数(String version, String uid)，version为客户端版本号，uid为用户id

```
    消息类型 | 提示消息                          | 类型描述
    0       | ""                               | "连接、获取信息",
    1       | ""                               | "添加好友打招呼消息",
    2       | ""                               | "普通聊天消息",
    3       | "群组已经创建好，可以和好友一起畅聊了" | "群创建成功",
    4       | ""                               | "好友申请",
    5       | ""                               | "媒体文件",
    6       | ""                               | "文件上传完成",
    7       | ""                               | "强制下线",
    8       | "群聊已解散"                      | "解散群聊",
    9       | "%s加入了群组"                    | "加入群聊",
    10      | ""                               | "更新昵称",
    11      | "%s退出了群聊"                    | "退出群聊",
    12      | "%s被管理员移出了群聊"             | "被管理员移出了群聊",
    13      | ""                               | "添加好友打招呼消息";

```
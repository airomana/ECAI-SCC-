# Room 数据库测试说明

## 一、仪器化测试（推荐）

在**连接真机或模拟器**的前提下运行：

```bash
cd ElderCareAI
./gradlew connectedDebugAndroidTest
```

或在 Android Studio：右键 `app/src/androidTest/.../ElderCareDatabaseTest.kt` → **Run 'ElderCareDatabaseTest'**。

测试覆盖：
- **DishDao**：插入、按名查询、模糊搜索，以及 `List<String>` 的 TypeConverter
- **HealthProfileDao**：插入、获取、更新，以及 `diseases`/`allergies` 的 TypeConverter
- **DiaryEntryDao**：插入、`getAll` 的 Flow
- **FridgeItemDao**：插入、`getCount`、`getAll`、`deleteById`

全部通过即表示 Room 与 TypeConverters 工作正常。

---

## 二、手动功能测试

安装 Debug 包到设备后，按下面步骤在 UI 里验证持久化。

### 1. 拍冰箱（FridgeItem）

1. 首页 → **拍冰箱**
2. **首次打开**：应看到 4 条预置食材（青菜、鸡蛋、牛奶、苹果），状态/过期提示正常
3. 在**过期**的食材上点 **删除**，该条应从列表消失
4. 完全退出 App 后重新打开 → 拍冰箱：删除的不会回来，其余仍存在

### 2. 今天吃了啥（DiaryEntry）

1. 首页 → **今天吃了啥**
2. 点麦克风开始录音 → 再点停止（当前为模拟，会有一段示例文字）
3. 点 **保存记录**，列表应出现一条新记录
4. 退出 App 再进入 **今天吃了啥**：刚保存的记录仍在

### 3. 健康档案（HealthProfile）

1. 首页 → 右上角 **设置** → **健康档案**
2. 填写姓名、年龄、疾病（如：`高血压, 糖尿病`）→ **保存**
3. 关闭弹窗后再次打开 **健康档案**：姓名、年龄、疾病应被正确回填

### 4. 菜品知识库（Dish）

预置数据在首次打开 DB 时写入，不通过 UI 直接操作。  
可通过 **拍菜单** 后续对接的“按菜名查知识库”逻辑间接验证；或运行上面的 `ElderCareDatabaseTest` 中 `dishDao_*` 用例。

---

## 三、快速校验命令

```bash
# 编译并安装 Debug
./gradlew installDebug

# 仅跑仪器化测试（需已连接设备）
./gradlew connectedDebugAndroidTest
```

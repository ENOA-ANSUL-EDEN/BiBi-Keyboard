# Lexi-Keyboard 颜色使用规范

> 版本：1.0
> 更新日期：2025-11-14
> 适用范围：所有UI组件开发

---

## 📖 目录

1. [概述](#概述)
2. [核心组件](#核心组件)
3. [Kotlin代码中的使用](#kotlin代码中的使用)
4. [XML布局中的使用](#xml布局中的使用)
5. [颜色令牌完整列表](#颜色令牌完整列表)
6. [常见错误和解决方案](#常见错误和解决方案)
7. [最佳实践](#最佳实践)
8. [FAQ](#faq)

---

## 概述

### 为什么需要统一的颜色系统？

Lexi-Keyboard 使用统一的颜色系统来实现以下目标：

- ✅ **主题一致性**：确保所有UI组件使用协调的配色方案
- ✅ **动态取色支持**：完整支持Android 12+ Monet动态配色
- ✅ **深色模式适配**：自动适配浅色/深色主题
- ✅ **可维护性**：集中管理颜色定义，避免硬编码
- ✅ **可扩展性**：为未来的自定义配色功能预留扩展点

### 设计架构

```
┌─────────────────────────────────────┐
│  业务代码 (Activities, Views, etc)  │
│  - 使用语义化颜色令牌                 │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│  UiColors.kt (颜色工具类)            │
│  - 提供统一的取色API                 │
│  - 封装MaterialColors.getColor()    │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│  UiColorTokens.kt (颜色令牌层)       │
│  - 定义语义化颜色令牌                 │
│  - 映射到Material3属性               │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│  Material3 主题系统                  │
│  - Monet动态取色                     │
│  - 深色/浅色模式                      │
└─────────────────────────────────────┘
```

---

## 核心组件

### UiColorTokens.kt

颜色语义令牌定义，将业务语义映射到Material3主题属性。

**文件位置**：`app/src/main/java/com/brycewg/asrkb/UiColorTokens.kt`

**作用**：
- 定义所有UI组件使用的颜色语义
- 提供清晰的命名，如`panelBg`（面板背景）、`kbdKeyFg`（键盘按键前景）
- 统一映射到Material3标准属性

**示例**：
```kotlin
object UiColorTokens {
    /** 面板背景色（主要容器） */
    val panelBg = com.google.android.material.R.attr.colorSurface

    /** 面板前景色（主要文本/图标） */
    val panelFg = com.google.android.material.R.attr.colorOnSurface

    /** 键盘按键背景 */
    val kbdKeyBg = com.google.android.material.R.attr.colorSurfaceVariant
}
```

### UiColors.kt

统一的颜色获取工具类，封装取色逻辑。

**文件位置**：`app/src/main/java/com/brycewg/asrkb/UiColors.kt`

**作用**：
- 提供统一的`get()`方法从主题获取颜色
- 提供便捷方法（如`panelBg()`, `panelFg()`）
- 内置回退颜色机制，确保在主题属性缺失时也能正常显示

**核心方法**：
```kotlin
object UiColors {
    // 通用取色方法（从View）
    fun get(view: View, @AttrRes attr: Int): Int

    // 通用取色方法（从Context）
    fun get(context: Context, @AttrRes attr: Int): Int

    // 便捷方法
    fun panelBg(view: View): Int
    fun panelFg(context: Context): Int
    fun error(view: View): Int
    // ... 更多便捷方法
}
```

---

## Kotlin代码中的使用

### 基本用法

#### 1. 导入必要的类

```kotlin
import com.brycewg.asrkb.UiColors
import com.brycewg.asrkb.UiColorTokens
```

#### 2. 使用通用`get()`方法

```kotlin
// 从View获取颜色
val backgroundColor = UiColors.get(view, UiColorTokens.panelBg)
view.setBackgroundColor(backgroundColor)

// 从Context获取颜色
val textColor = UiColors.get(context, UiColorTokens.panelFg)
textView.setTextColor(textColor)
```

#### 3. 使用便捷方法（推荐）

```kotlin
// 设置背景色
view.setBackgroundColor(UiColors.panelBg(view))

// 设置文本颜色
textView.setTextColor(UiColors.panelFg(context))

// 设置图标着色
imageView.setColorFilter(UiColors.floatingIcon(view))

// 获取错误色
errorView.setTextColor(UiColors.error(context))
```

### 实际使用示例

#### 示例1：设置TextView颜色

```kotlin
private fun createTextView(context: Context): TextView {
    return TextView(context).apply {
        // ✅ 正确：使用UiColors
        setTextColor(UiColors.panelFg(this))
        setBackgroundColor(UiColors.panelBg(context))

        // ❌ 错误：直接使用MaterialColors
        // setTextColor(MaterialColors.getColor(this, R.attr.colorOnSurface))

        // ❌ 错误：硬编码颜色
        // setTextColor(0xFF000000.toInt())
        // setTextColor(Color.BLACK)
    }
}
```

#### 示例2：在自定义View中使用

```kotlin
class CustomKeyView(context: Context) : View(context) {
    private val paint = Paint()

    init {
        // ✅ 正确：在初始化时获取颜色
        paint.color = UiColors.get(context, UiColorTokens.kbdKeyFg)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 使用paint绘制
        canvas.drawText("A", 0f, 0f, paint)
    }
}
```

#### 示例3：动态创建UI元素

```kotlin
private fun addMenuItem(container: ViewGroup, label: String) {
    val textView = TextView(container.context).apply {
        text = label
        // ✅ 使用便捷方法
        setTextColor(UiColors.panelFgVariant(this))
        textSize = 14f
    }
    container.addView(textView)
}
```

#### 示例4：设置状态颜色

```kotlin
private fun updateSelectionState(view: MaterialCardView, isSelected: Boolean) {
    val color = if (isSelected) {
        UiColors.selectedBg(view)
    } else {
        UiColors.panelBg(view)
    }
    view.setCardBackgroundColor(color)
}
```

---

## XML布局中的使用

### 基本原则

在XML布局中，**必须使用Material Design主题属性**，不要硬编码颜色值。

### 推荐用法

#### 1. 文本颜色

```xml
<!-- ✅ 正确：使用Material属性 -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textColor="?attr/colorOnSurface"
    android:text="标题文本" />

<!-- ✅ 正确：使用次要文本颜色 -->
<TextView
    android:textColor="?attr/colorOnSurfaceVariant"
    android:text="说明文本" />

<!-- ❌ 错误：硬编码颜色 -->
<TextView
    android:textColor="#000000"
    android:text="错误示例" />

<!-- ❌ 错误：使用Android内置颜色 -->
<TextView
    android:textColor="@android:color/darker_gray"
    android:text="错误示例" />
```

#### 2. 背景颜色

```xml
<!-- ✅ 正确：使用Surface颜色 -->
<LinearLayout
    android:background="?attr/colorSurface">
    <!-- 内容 -->
</LinearLayout>

<!-- ✅ 正确：使用容器变体色 -->
<View
    android:background="?attr/colorSurfaceVariant" />
```

#### 3. Material组件

```xml
<!-- ✅ 正确：FAB按钮使用Primary容器色 -->
<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fab"
    app:backgroundTint="?attr/colorPrimaryContainer"
    app:tint="?attr/colorOnPrimaryContainer" />

<!-- ✅ 正确：MaterialButton -->
<com.google.android.material.button.MaterialButton
    android:textColor="?attr/colorOnPrimary"
    app:backgroundTint="?attr/colorPrimary" />

<!-- ✅ 正确：MaterialCardView -->
<com.google.android.material.card.MaterialCardView
    app:cardBackgroundColor="?attr/colorSurface"
    app:strokeColor="?attr/colorOutline">
    <!-- 内容 -->
</com.google.android.material.card.MaterialCardView>
```

#### 4. 图标着色

```xml
<!-- ✅ 正确：ImageView使用tint -->
<ImageView
    android:src="@drawable/ic_settings"
    android:tint="?attr/colorOnSurface" />

<!-- ✅ 正确：ImageButton -->
<ImageButton
    android:src="@drawable/ic_close"
    app:tint="?attr/colorOnSurfaceVariant" />
```

### XML颜色属性对照表

| 用途 | Material属性 | UiColorTokens对应 |
|------|-------------|-------------------|
| 主要背景 | `?attr/colorSurface` | `panelBg` |
| 主要前景 | `?attr/colorOnSurface` | `panelFg` |
| 次要前景 | `?attr/colorOnSurfaceVariant` | `panelFgVariant` |
| 容器背景 | `?attr/colorSurfaceVariant` | `containerBg` |
| 主强调色 | `?attr/colorPrimary` | `primary` |
| 主强调容器 | `?attr/colorPrimaryContainer` | `primaryContainer` |
| 次强调色 | `?attr/colorSecondary` | `secondary` |
| 选中背景 | `?attr/colorSecondaryContainer` | `selectedBg` |
| 错误色 | `?attr/colorError` | `error` |
| 边框色 | `?attr/colorOutline` | `outline` |

---

## 颜色令牌完整列表

### 面板与容器

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `panelBg` | 面板背景色 | `colorSurface` | Activity背景、对话框背景 |
| `panelFg` | 面板前景色 | `colorOnSurface` | 主要文本、主要图标 |
| `panelFgVariant` | 面板前景色（次要） | `colorOnSurfaceVariant` | 次要文本、说明文本 |
| `containerBg` | 容器背景色 | `colorSurfaceVariant` | 卡片背景、芯片背景 |
| `containerFg` | 容器前景色 | `colorOnSurfaceVariant` | 容器内文本 |

### 键盘相关

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `kbdKeyBg` | 键盘按键背景 | `colorSurfaceVariant` | 按键背景 |
| `kbdKeyFg` | 键盘按键文本/图标 | `colorOnSurfaceVariant` | 按键文字、图标 |
| `kbdContainerBg` | 键盘容器背景 | `colorSurface` | 键盘整体背景 |

### 强调与状态色

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `primary` | 主强调色 | `colorPrimary` | 主要操作按钮 |
| `primaryContainer` | 主强调容器色 | `colorPrimaryContainer` | FAB背景 |
| `onPrimaryContainer` | 主强调容器前景色 | `colorOnPrimaryContainer` | FAB图标 |
| `secondary` | 次要强调色 | `colorSecondary` | 次要操作、强调元素 |
| `secondaryContainer` | 次要强调容器色 | `colorSecondaryContainer` | 选中状态背景 |
| `onSecondaryContainer` | 次要强调容器前景色 | `colorOnSecondaryContainer` | 选中状态文字 |
| `tertiary` | 第三强调色 | `colorTertiary` | 装饰性元素 |
| `error` | 错误/警告色 | `colorError` | 错误提示、警告 |
| `errorContainer` | 错误容器色 | `colorErrorContainer` | 错误背景 |
| `onErrorContainer` | 错误容器前景色 | `colorOnErrorContainer` | 错误文字 |

### 选中与高亮

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `selectedBg` | 选中项背景色 | `colorSecondaryContainer` | 列表项选中状态 |
| `selectedFg` | 选中项前景色 | `colorOnSecondaryContainer` | 选中项文字 |
| `ripple` | 波纹/高亮效果色 | `colorControlHighlight` | 点击波纹效果 |

### 边框与分割线

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `outline` | 主要边框色 | `colorOutline` | 卡片边框、分割线 |
| `outlineVariant` | 次要边框色 | `colorOutlineVariant` | 更淡的边框 |

### 悬浮球相关

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `floatingBallBg` | 悬浮球容器背景 | `colorSurface` | 悬浮球背景 |
| `floatingIcon` | 悬浮球图标色 | `colorSecondary` | 悬浮球图标 |
| `floatingError` | 悬浮球错误状态色 | `colorError` | 错误状态指示 |

### 状态芯片

| 令牌名称 | 说明 | Material属性 | 使用场景 |
|---------|------|-------------|---------|
| `chipBg` | 芯片背景色 | `colorSurfaceVariant` | Chip背景 |
| `chipFg` | 芯片文本色 | `colorOnSurfaceVariant` | Chip文字 |

---

## 常见错误和解决方案

### ❌ 错误1：直接使用MaterialColors

**错误代码**：
```kotlin
val color = MaterialColors.getColor(view, R.attr.colorOnSurface)
```

**问题**：绕过了UiColors抽象层，不利于后续扩展

**正确做法**：
```kotlin
val color = UiColors.get(view, UiColorTokens.panelFg)
// 或使用便捷方法
val color = UiColors.panelFg(view)
```

---

### ❌ 错误2：硬编码颜色值

**错误代码**：
```kotlin
view.setBackgroundColor(0xFFFFFFFF.toInt())
textView.setTextColor(Color.BLACK)
```

**问题**：
- 深色模式下显示异常
- 无法跟随Monet动态配色
- 破坏主题一致性

**正确做法**：
```kotlin
view.setBackgroundColor(UiColors.panelBg(view))
textView.setTextColor(UiColors.panelFg(context))
```

---

### ❌ 错误3：在XML中硬编码颜色

**错误代码**：
```xml
<TextView
    android:textColor="#000000"
    android:text="文本" />

<TextView
    android:textColor="@android:color/darker_gray"
    android:text="文本" />
```

**问题**：同硬编码颜色值问题

**正确做法**：
```xml
<TextView
    android:textColor="?attr/colorOnSurface"
    android:text="文本" />
```

---

### ❌ 错误4：引用未定义的颜色资源

**错误代码**：
```xml
<FloatingActionButton
    app:backgroundTint="@color/fab_mic_tint" />
```

**问题**：资源不存在，可能导致编译错误或运行时崩溃

**正确做法**：
```xml
<FloatingActionButton
    app:backgroundTint="?attr/colorPrimaryContainer" />
```

---

### ❌ 错误5：忘记导入UiColors

**错误代码**：
```kotlin
val color = UiColors.panelBg(view) // 编译错误：未解析的引用
```

**问题**：未导入必要的类

**正确做法**：
```kotlin
import com.brycewg.asrkb.UiColors
import com.brycewg.asrkb.UiColorTokens

val color = UiColors.panelBg(view)
```

---

## 最佳实践

### 1. 优先使用便捷方法

```kotlin
// ✅ 推荐：使用便捷方法
val backgroundColor = UiColors.panelBg(view)

// ⚪ 可以：使用通用方法
val backgroundColor = UiColors.get(view, UiColorTokens.panelBg)

// ❌ 不推荐：直接使用MaterialColors
val backgroundColor = MaterialColors.getColor(view, R.attr.colorSurface)
```

### 2. 在Kotlin中使用UiColors，在XML中使用Material属性

```kotlin
// Kotlin代码
textView.setTextColor(UiColors.panelFg(this))
```

```xml
<!-- XML布局 -->
<TextView android:textColor="?attr/colorOnSurface" />
```

### 3. 为自定义View添加try-catch保护

```kotlin
class CustomView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = try {
            UiColors.get(context, UiColorTokens.kbdKeyFg)
        } catch (e: Throwable) {
            0xFF222222.toInt() // 安全回退
        }
    }
}
```

### 4. 集中管理颜色相关代码

```kotlin
// ✅ 推荐：在一个方法中统一设置颜色
private fun applyThemeColors() {
    titleText.setTextColor(UiColors.panelFg(this))
    subtitleText.setTextColor(UiColors.panelFgVariant(this))
    container.setBackgroundColor(UiColors.panelBg(this))
}

// ❌ 不推荐：分散在各处
override fun onCreate() {
    titleText.setTextColor(UiColors.panelFg(this))
    // ... 100行代码
    subtitleText.setTextColor(UiColors.panelFgVariant(this))
}
```

### 5. 颜色更新时考虑性能

```kotlin
// ✅ 推荐：缓存颜色值（如果需要频繁使用）
private val cachedPrimaryColor by lazy { UiColors.primary(context) }

override fun onDraw(canvas: Canvas) {
    paint.color = cachedPrimaryColor
    canvas.drawCircle(x, y, radius, paint)
}

// ⚪ 可以接受：每次获取（如果不频繁）
fun updateColor() {
    view.setBackgroundColor(UiColors.panelBg(view))
}
```

---

## FAQ

### Q1: UiColors和Material3主题有什么区别？

**A**: UiColors是对Material3主题的封装和抽象：

- **Material3主题**：Android系统级别的主题系统，定义了标准颜色属性
- **UiColors**：应用层的抽象工具，提供语义化的颜色令牌和便捷方法
- **优势**：为未来自定义配色功能预留扩展点，同时保持代码清晰

### Q2: 什么时候应该添加新的颜色令牌？

**A**: 在以下情况下考虑添加新令牌：

1. 发现多处代码使用相同的颜色语义
2. 需要为特定UI组件定义专属配色
3. 现有令牌无法准确表达颜色用途

**添加步骤**：
1. 在`UiColorTokens.kt`中添加新令牌
2. 映射到合适的Material3属性
3. 在`UiColors.kt`的`getDefaultFallback()`中添加回退值
4. （可选）在`UiColors.kt`中添加便捷方法

### Q3: 如何处理特殊颜色需求（如splash背景）？

**A**: 对于不需要动态取色的固定颜色，可以在`res/values/colors.xml`中定义：

```xml
<resources>
    <!-- 固定颜色（不受Monet影响） -->
    <color name="splash_background">#FFFFFFFF</color>
    <color name="ic_launcher_background">#FFFFFFFF</color>
</resources>
```

**注意**：这类颜色应该：
- 用途明确（如启动屏、应用图标）
- 在注释中说明不参与动态取色
- 数量尽可能少

### Q4: 深色模式如何处理？

**A**: UiColors系统自动支持深色模式：

1. Material3主题会根据系统设置自动切换
2. 无需在代码中手动判断深色/浅色模式
3. 如需为深色模式定义特殊颜色，在`res/values-night/`目录添加资源文件

### Q5: 遇到颜色显示异常怎么办？

**A**: 检查清单：

1. ✅ 确认已导入`UiColors`和`UiColorTokens`
2. ✅ 检查是否使用了正确的颜色令牌
3. ✅ 验证XML布局中使用`?attr/`而非硬编码
4. ✅ 查看Logcat是否有相关错误信息
5. ✅ 确认主题正确应用（`Theme.ASRKeyboard`）

---

## 附录：代码检查清单

在提交代码前，请确保：

- [ ] Kotlin代码中所有颜色都通过`UiColors`获取
- [ ] XML布局中所有颜色都使用Material属性（`?attr/xxx`）
- [ ] 没有硬编码的颜色值（如`#FFFFFF`, `Color.BLACK`）
- [ ] 没有使用Android内置颜色（如`@android:color/darker_gray`）
- [ ] 新增的颜色令牌已在`UiColorTokens.kt`中定义
- [ ] 已添加必要的导入语句
- [ ] 颜色使用符合语义（如背景用`panelBg`，文字用`panelFg`）

---

## 相关资源

- [Material Design 3 颜色系统](https://m3.material.io/styles/color/system/overview)
- [Android 动态颜色 (Monet)](https://developer.android.com/develop/ui/views/theming/dynamic-colors)
- [UiColors.kt 源代码](../app/src/main/java/com/brycewg/asrkb/UiColors.kt)
- [UiColorTokens.kt 源代码](../app/src/main/java/com/brycewg/asrkb/UiColorTokens.kt)

---

**文档维护**：如发现文档错误或需要补充，请联系项目维护者或提交Issue。

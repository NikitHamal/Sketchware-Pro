package mod.pranav.viewbinding

import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ViewBindingBuilder(
    private val inputFiles: List<File>,
    private val outputDir: File,
    private val packageName: String = "dev.pranav.viewbinding"
) {
    fun generateBindings() {
        inputFiles.forEach { generateBindingForLayoutAndWrite(it) }
    }

    /** generate binding and return class code */
    fun generateBindingForLayout(layoutFile: File): String {
        val document = parseDocument(layoutFile)
        val rootView = getTopLevelView(document.documentElement)
        val views = parseViews(document.documentElement, rootView)
        val name = generateFileNameForLayout(layoutFile.nameWithoutExtension)

        val content = buildString {
            appendLine("// Generated file. Do not modify.")
            appendLine("package $packageName;")
            appendLine()
            appendLine(generateImports(views, rootView))
            appendLine()
            appendLine("public final class $name {")
            appendLine("    public final ${rootView.type} ${rootView.name};")
            views.forEach { appendLine("    public final ${it.type} ${it.name};") }
            appendLine()
            append("    private $name(${rootView.type} ${rootView.name}")
            if (views.isNotEmpty()) {
                append(views.joinToString(prefix = ", ") { "${it.type} ${it.name}" })
            }
            appendLine(") {")
            appendLine("        this.${rootView.name} = ${rootView.name};")
            views.forEach { appendLine("        this.${it.name} = ${it.name};") }
            appendLine("    }")
            appendLine()
            appendLine("    public ${rootView.type} getRoot() {")
            appendLine("        return ${rootView.name};")
            appendLine("    }")
            appendLine()
            appendLine("    public static $name inflate(LayoutInflater inflater) {")
            if (rootView.isMergeRoot) {
                appendLine("        FrameLayout parent = new FrameLayout(inflater.getContext());")
                appendLine("        return inflate(inflater, parent, true);")
            } else {
                appendLine("        return inflate(inflater, null, false);")
            }
            appendLine("    }")
            appendLine()
            appendLine("    public static $name inflate(LayoutInflater inflater, ViewGroup parent, boolean attachToParent) {")
            if (rootView.isMergeRoot) {
                appendLine("        if (parent == null) {")
                appendLine("            throw new NullPointerException(\"parent\");")
                appendLine("        }")
                appendLine("        inflater.inflate(R.layout.${layoutFile.nameWithoutExtension}, parent, true);")
                appendLine("        return bind(parent);")
            } else {
                appendLine("        View root = inflater.inflate(R.layout.${layoutFile.nameWithoutExtension}, parent, false);")
                appendLine("        if (attachToParent && parent != null) {")
                appendLine("            parent.addView(root);")
                appendLine("        }")
                appendLine("        return bind(root);")
            }
            appendLine("    }")
            appendLine()
            appendLine("    public static $name bind(View view) {")
            appendLine("        ${rootView.type} ${rootView.name} = (${rootView.type}) view;")
            if (views.isNotEmpty()) {
                views.forEach { parsedView ->
                    if (parsedView.isInclude) {
                        appendLine("        View ${parsedView.name}View = view.findViewById(R.id.${parsedView.id});")
                        appendLine("        ${parsedView.type} ${parsedView.name} = ${parsedView.name}View != null ? ${parsedView.fullType}.bind(${parsedView.name}View) : null;")
                    } else {
                        appendLine("        ${parsedView.type} ${parsedView.name} = (${parsedView.type}) view.findViewById(R.id.${parsedView.id});")
                    }
                }
                appendLine("        if (${views.joinToString(" || ") { "${it.name} == null" }}) {")
                appendLine("            throw new IllegalStateException(\"Required views are missing\");")
                appendLine("        }")
                appendLine()
            }
            append("        return new $name(${rootView.name}")
            if (views.isNotEmpty()) {
                append(views.joinToString(prefix = ", ") { it.name })
            }
            appendLine(");")
            appendLine("    }")
            appendLine("}")
        }

        return content
    }

    /** generate view binding and save in output file */
    private fun generateBindingForLayoutAndWrite(layoutFile: File) {
        val name = generateFileNameForLayout(layoutFile.nameWithoutExtension)
        val file = File(outputDir, "$name.java")
        val content = generateBindingForLayout(layoutFile)
        file.writeText(content)
    }

    private fun generateImports(views: List<ParsedView>, rootView: ParsedView): String {
        val imports = linkedSetOf(
            "import android.view.LayoutInflater;",
            "import android.view.View;",
            "import android.view.ViewGroup;",
            "import android.widget.FrameLayout;",
            "import ${rootView.fullType};",
            "import ${packageName.substringBeforeLast(".")}.R;"
        )

        views.distinctBy { it.fullType }.forEach {
            imports.add("import ${it.fullType};")
        }

        return imports.joinToString("\n")
    }

    private fun parseDocument(layoutFile: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile)

    private fun getTopLevelView(rootNode: Node): ParsedView {
        if (rootNode.nodeName == "merge") {
            return ParsedView("View", "android.view.View", "rootView", isRoot = true, isMergeRoot = true)
        }

        val id = rootNode.attributes?.getNamedItem("android:id")?.nodeValue?.substringAfter("/") ?: "rootView"
        val resolved = resolveViewClass(rootNode.nodeName)
        return ParsedView(resolved.type, resolved.fullType, id, isRoot = true)
    }

    private fun parseViews(rootNode: Node, rootView: ParsedView): List<ParsedView> {
        val regularViews = LinkedHashMap<String, ParsedView>()
        val includeViews = LinkedHashMap<String, ParsedView>()
        parseNode(rootNode, regularViews, includeViews)

        if (!rootView.isMergeRoot) {
            regularViews.remove(rootView.id)
        }

        return regularViews.values.toList() + includeViews.values.toList()
    }

    private fun parseNode(node: Node, regularViews: LinkedHashMap<String, ParsedView>, includeViews: LinkedHashMap<String, ParsedView>) {
        if (node.nodeType != Node.ELEMENT_NODE) {
            return
        }

        val attributes = node.attributes
        val nodeName = node.nodeName
        val idNode = attributes?.getNamedItem("android:id")

        when (nodeName) {
            "include" -> {
                val layoutAttr = attributes?.getNamedItem("layout")?.nodeValue?.substringAfter("/")
                if (layoutAttr != null && idNode != null) {
                    val id = idNode.nodeValue.substringAfter("/")
                    includeViews.putIfAbsent(
                        id,
                        ParsedView(
                            generateFileNameForLayout(layoutAttr),
                            "$packageName.${generateFileNameForLayout(layoutAttr)}",
                            id,
                            isInclude = true
                        )
                    )
                }
            }
            "merge", "data", "variable", "import", "requestFocus" -> {
                // no binding field for special/non-view nodes
            }
            "fragment" -> {
                if (idNode != null) {
                    val id = idNode.nodeValue.substringAfter("/")
                    regularViews.putIfAbsent(id, ParsedView("View", "android.view.View", id))
                }
            }
            else -> {
                if (idNode != null) {
                    val id = idNode.nodeValue.substringAfter("/")
                    val resolved = resolveViewClass(nodeName)
                    regularViews.putIfAbsent(id, ParsedView(resolved.type, resolved.fullType, id))
                }
            }
        }

        for (i in 0 until node.childNodes.length) {
            parseNode(node.childNodes.item(i), regularViews, includeViews)
        }
    }

    private fun resolveViewClass(tagName: String): ResolvedType {
        if (tagName.contains('.')) {
            return ResolvedType(tagName.substringAfterLast('.'), tagName)
        }

        val fullType = knownTagMappings[tagName] ?: "android.widget.$tagName"
        return ResolvedType(fullType.substringAfterLast('.'), fullType)
    }

    data class ResolvedType(
        val type: String,
        val fullType: String
    )

    data class ParsedView(
        val type: String,
        val fullType: String,
        val id: String,
        val isInclude: Boolean = false,
        val isRoot: Boolean = false,
        val isMergeRoot: Boolean = false
    ) {
        val name = generateParameterFromId(id)

        override fun toString(): String {
            return "${type}(fullName='$fullType', id='$id', name='$name', isInclude=$isInclude, isRoot=$isRoot, isMergeRoot=$isMergeRoot)"
        }
    }

    companion object {
        private val knownTagMappings = mapOf(
            "View" to "android.view.View",
            "ViewGroup" to "android.view.ViewGroup",
            "ViewStub" to "android.view.ViewStub",
            "TextureView" to "android.view.TextureView",
            "SurfaceView" to "android.view.SurfaceView",
            "WebView" to "android.webkit.WebView",
            "NestedScrollView" to "androidx.core.widget.NestedScrollView",
            "RecyclerView" to "androidx.recyclerview.widget.RecyclerView",
            "ConstraintLayout" to "androidx.constraintlayout.widget.ConstraintLayout",
            "Group" to "androidx.constraintlayout.widget.Group",
            "Barrier" to "androidx.constraintlayout.widget.Barrier",
            "Guideline" to "androidx.constraintlayout.widget.Guideline",
            "CardView" to "androidx.cardview.widget.CardView",
            "CoordinatorLayout" to "androidx.coordinatorlayout.widget.CoordinatorLayout",
            "DrawerLayout" to "androidx.drawerlayout.widget.DrawerLayout",
            "SwipeRefreshLayout" to "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
            "ViewPager" to "androidx.viewpager.widget.ViewPager",
            "ViewPager2" to "androidx.viewpager2.widget.ViewPager2",
            "FragmentContainerView" to "androidx.fragment.app.FragmentContainerView",
            "Toolbar" to "androidx.appcompat.widget.Toolbar",
            "AppCompatCheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "AppBarLayout" to "com.google.android.material.appbar.AppBarLayout",
            "CollapsingToolbarLayout" to "com.google.android.material.appbar.CollapsingToolbarLayout",
            "MaterialToolbar" to "com.google.android.material.appbar.MaterialToolbar",
            "FloatingActionButton" to "com.google.android.material.floatingactionbutton.FloatingActionButton",
            "ExtendedFloatingActionButton" to "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton",
            "MaterialButton" to "com.google.android.material.button.MaterialButton",
            "MaterialButtonToggleGroup" to "com.google.android.material.button.MaterialButtonToggleGroup",
            "MaterialSplitButton" to "com.google.android.material.button.MaterialSplitButton",
            "MaterialCardView" to "com.google.android.material.card.MaterialCardView",
            "Chip" to "com.google.android.material.chip.Chip",
            "ChipGroup" to "com.google.android.material.chip.ChipGroup",
            "TabLayout" to "com.google.android.material.tabs.TabLayout",
            "TabItem" to "com.google.android.material.tabs.TabItem",
            "TextInputLayout" to "com.google.android.material.textfield.TextInputLayout",
            "TextInputEditText" to "com.google.android.material.textfield.TextInputEditText",
            "MaterialAutoCompleteTextView" to "com.google.android.material.textfield.MaterialAutoCompleteTextView",
            "BottomNavigationView" to "com.google.android.material.bottomnavigation.BottomNavigationView",
            "NavigationRailView" to "com.google.android.material.navigationrail.NavigationRailView",
            "Slider" to "com.google.android.material.slider.Slider",
            "MaterialSwitch" to "com.google.android.material.materialswitch.MaterialSwitch",
            "MaterialCheckBox" to "com.google.android.material.checkbox.MaterialCheckBox",
            "ShapeableImageView" to "com.google.android.material.imageview.ShapeableImageView",
            "MaterialDivider" to "com.google.android.material.divider.MaterialDivider",
            "CircularProgressIndicator" to "com.google.android.material.progressindicator.CircularProgressIndicator",
            "LinearProgressIndicator" to "com.google.android.material.progressindicator.LinearProgressIndicator",
            "BottomSheetDragHandleView" to "com.google.android.material.bottomsheet.BottomSheetDragHandleView",
            "SearchBar" to "com.google.android.material.search.SearchBar",
            "SearchView" to "android.widget.SearchView"
        )

        @JvmStatic
        fun generateParameterFromId(id: String): String {
            return if (id.contains('_')) id.substringBefore('_') + id.substringAfter('_')
                .split('_')
                .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } } else id
        }

        @JvmStatic
        fun generateFileNameForLayout(layoutName: String): String {
            return layoutName.split('_')
                .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } } + "Binding"
        }
    }
}

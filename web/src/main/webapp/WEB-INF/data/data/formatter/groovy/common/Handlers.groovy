package common

import org.fao.geonet.services.metadata.format.groovy.Environment
import org.fao.geonet.services.metadata.format.groovy.util.MenuAction
import org.fao.geonet.services.metadata.format.groovy.util.NavBarItem
import org.fao.geonet.services.metadata.format.groovy.util.Summary

public class Handlers {
    private org.fao.geonet.services.metadata.format.groovy.Handlers handlers;
    private org.fao.geonet.services.metadata.format.groovy.Functions f
    private Environment env

    common.Matchers matchers
    common.Functions func

    public Handlers(handlers, f, env) {
        this.handlers = handlers
        this.f = f
        this.env = env
        func = new common.Functions(handlers: handlers, f:f, env:env)
        matchers =  new common.Matchers(handlers: handlers, f:f, env:env)
    }

    def addDefaultStartAndEndHandlers() {
        handlers.start htmlOrXmlStart
        handlers.end htmlOrXmlEnd
    }

    def entryEl(labeller) {
        return entryEl(labeller, null)
    }
    /**
     * Creates a function that will process all children and sort then according to the sorter that applies to the elements. Then
     * returns the default html for the container elements.
     *
     * @param labeller a function for creating a label from the element
     * @param classer a function taking the element class(es) to add to the entry element.  The method should return a string.
     */
    def entryEl(labeller, classer) {
        return { el ->
            def childData = handlers.processElements(el.children(), el);
            def replacement = [label: labeller(el), childData: childData, name:'']

            if (classer != null) {
                replacement.name = classer(el);
            }

            if (!childData.isEmpty()) {
                return handlers.fileResult('html/2-level-entry.html', replacement)
            }
            return null
        }
    }
    def processChildren(childSelector) {
        return {el ->
            handlers.processElements(childSelector(el), el);
        }
    }
    /**
     * Creates a function which will:
     *
     * 1. Select a single element using the selector function
     * 2. Process all children of the element selected in step 1 with sorter that applies to the element selected in step 1
     * 3. Create a label using executing the labeller on the element passed to handler functions (not element selected in step 1)
     *
     * @param selector a function that will select a single element from the descendants of the element passed to it
     * @param labeller a function for creating a label from the element
     */
    def flattenedEntryEl(selector, labeller) {
        return { parentEl ->
            def el = selector(parentEl)
            def childData = handlers.processElements(el.children(), el);

            if (!childData.isEmpty()) {
                return handlers.fileResult('html/2-level-entry.html', [label: labeller(el), childData: childData])
            }
            return null
        }
    }

    def selectIsotype(name) {
        return {
            it.children().find { ch ->
                ch.name() == name || ch['@gco:isoType'].text() == name
            }
        }
    }

    def htmlOrXmlStart = {
        if (func.isHtmlOutput()) {
            def minimize = ''
            if (env.param("debug").toBool()) {
                minimize = '?minimize=false'
            }
            return """
<!DOCTYPE html>
<html>
<head lang="en">
    <meta charset="UTF-8"/>
    <link rel="stylesheet" href="../../static/gn_bootstrap.css$minimize"/>
    <link rel="stylesheet" href="../../static/gn_metadata.css$minimize"/>
    <script src="../../static/lib.js$minimize"></script>
</head>
<body>
"""
        } else {
            return ''
        }
    }

    def htmlOrXmlEnd = {
        def required = """
<script type="text/javascript">
//<![CDATA[
        ${handlers.fileResult("js/std-footer.js", [:])}
//]]>
</script>"""
        if (func.isHtmlOutput()) {
            return required + '</body></html>'
        } else {
            return required
        }
    }


    NavBarItem createXmlNavBarItem() {
        return new NavBarItem(f.translate("xml"), f.translate("xml"), "", "xml.metadata.get?uuid=${env.metadataUUID}")
    }
    def configureSummaryActionMenu(Summary summary) {
        def url = env.localizedUrl
        if (env.canEdit()) {
            summary.actions << new MenuAction(label: "edit", javascript: "window.open('catalog.edit#/metadata/${this.env.metadataId}')", iconClasses: "fa fa-edit")
            def publishUrl = {
                def on = it ? "on" : "off"
                "md.privileges.update?update=true&id=${env.metadataId}&_1_0=$on&_1_1=$on&_1_5=$on&_1_6=$on"
            }

            def basicPublicJs = { isPublish ->
                """\$.ajax({
                        url: '${publishUrl(isPublish)}',
                        success: function() {
                            \$('li#menu-action-publish').toggleClass('disabled');
                            \$('li#menu-action-unpublish').toggleClass('disabled');
                        }
                      })""".replaceAll(/\s+/, " ")
            }


            def published = env.indexInfo.get("_groupPublished").contains("all") || env.indexInfo.get("_groupPublished").contains("guest")
            def publishAction = new MenuAction(label: "publish", javascript: basicPublicJs(true), iconClasses: "fa fa-unlock", liClasses: "disabled")
            summary.actions << publishAction
            if (!published && env.indexInfo.get("_valid").contains("1")) {
                publishAction.liClasses = ""
            }
            def unpublishAction = new MenuAction(label: "unpublish", javascript: basicPublicJs(false), iconClasses: "fa fa-lock", liClasses: "disabled")
            summary.actions << unpublishAction
            if (published) {
                unpublishAction.liClasses = ""
            }
        }
        summary.actions << new MenuAction(label: "export", iconClasses: "fa fa-share-alt", submenu: [
                new MenuAction(label: "exportRdf", javascript: "window.location.href = 'rdf.metadata.get?uuid=${this.env.metadataUUID}'", iconClasses: "fa fa-rss"),
                new MenuAction(label: "exportPdf", javascript: "window.open('md.format.pdf?xsl=full_view&uuid=${this.env.metadataUUID}')", iconClasses: "fa fa-print"),
                new MenuAction(label: "exportZip", javascript: "window.location.href = 'mef.export?version=2&uuid=${this.env.metadataUUID}'", iconClasses: "fa fa-archive")
        ]);

        def shareURL = { it + URLEncoder.encode("${url}md.format.html?xsl=full_view&uuid=${this.env.metadataUUID}", "UTF-8") }
        summary.actions << new MenuAction(label: "share", iconClasses: "fa fa-share", submenu: [
                new MenuAction(label: "googlePlus", javascript: "window.open('${shareURL('https://plus.google.com/share?url=')}')", iconClasses: "fa fa-google-plus"),
                new MenuAction(label: "twitter", javascript: "window.open('${shareURL('https://twitter.com/share?url=')}')", iconClasses: "fa fa-twitter"),
                new MenuAction(label: "facebook", javascript: "window.open('${shareURL('href="https://www.facebook.com/sharer.php?u=')}')", iconClasses: "fa fa-facebook")
        ]);
    }
}
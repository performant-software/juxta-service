
<table>
    <tr><td align="right">Parsing Template:</td><td>${template.name}</td></tr>
    <tr><td align="right">Root Tag Name:</td><td>${template.rootElement.namespaceUri}:${template.rootElement.namespacePrefix}:${template.rootElement.localName}</td></tr>
    <tr><td align="right">Is Default:</td><td>${template.default?string("Yes","No")}</td></tr>
</table>

<br/>

<table>
    <tr>
        <th>XML Tag (URI : Prefix : Local Name)</th>
        <th>Action</th>
    </tr>
    <#list template.tagActions as action>
        <tr>
            <td>
                ${action.tag.namespaceUri} : ${action.tag.namespacePrefix} : ${action.tag.localName}
            </td>
            <td>${action.action}</td>
        </tr>
    </#list>
</table>
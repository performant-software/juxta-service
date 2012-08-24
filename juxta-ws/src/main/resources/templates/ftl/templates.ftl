

<table>
    <tr>
        <th>ID</th><th>Name</th><th>Root Element</th><th>Is Default</th>
    </tr>
    <#list templates as template>
        <tr>
            <td align="center"><a href="${baseUrl}/${workspace}/template/${template.id}">${template.id}</a></td>
            <td><a href="${baseUrl}/${workspace}/template/${template.id}">${template.name}</a></td>
            <td>${template.rootElement.namespaceUri}:${template.rootElement.namespacePrefix}:${template.rootElement.localName}</td>
            <td align="center">${template.default?string("Yes","No")}</td>
        </tr>
    </#list>
</table>
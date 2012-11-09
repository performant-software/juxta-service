
<table>
    <tr>
        <th>ID</th><th>Name</th><th>Type</th>
    </tr>
    <#list docs as doc>
        <tr>
            <td><a href="${baseUrl}/${workspace}/source/${doc.id}">${doc.id}</a></td>
            <td><a href="${baseUrl}/${workspace}/source/${doc.id}">${doc.name}</a></td>
            <td>${doc.type}</td>
        </tr>
    </#list>
</table>
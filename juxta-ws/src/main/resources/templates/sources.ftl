
<table>
    <tr>
        <th>ID</th><th>File Name</th><th>File Type</th>
    </tr>
    <#list docs as doc>
        <tr>
            <td><a href="${baseUrl}/${workspace}/source/${doc.id}">${doc.id}</a></td>
            <td><a href="${baseUrl}/${workspace}/source/${doc.id}">${doc.fileName}</a></td>
            <td>${doc.text.type}</td>
        </tr>
    </#list>
</table>
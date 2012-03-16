
   
<table border="1" cellpadding="10">
    <tr>
        <th>ID</th><th>Name</th>
    </tr>
    <#list items as item>
        <tr>
            <td><a href="${baseUrl}/${workspace}/filter/${item.id}">${item.id}</a></td>
            <td><a href="${baseUrl}/${workspace}/filter/${item.id}">${item.name}</a></td>
        </tr>
    </#list>
</table>

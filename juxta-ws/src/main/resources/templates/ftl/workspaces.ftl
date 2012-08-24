
<table>
    <tr>
        <th>Name</th><th>Description</th>
    </tr>
    <#list workspaces as ws>
        <tr>
            <td>${ws.name}</td>
            <td>${ws.description}</td>
        </tr>
    </#list>
</table>
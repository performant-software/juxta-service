

<div class="sets-div-left">
    <table style="width:100%;">
        <tr>
            <th>ID</th><th>Name</th>
        </tr>
        <#list items as item>
            <tr>
                <td><a href="${baseUrl}/${workspace}/set/${item.id}">${item.id}</a></td>
                <td><a href="${baseUrl}/${workspace}/set/${item.id}">${item.name}</a></td>
            </tr>
        </#list>
    </table>
</div>


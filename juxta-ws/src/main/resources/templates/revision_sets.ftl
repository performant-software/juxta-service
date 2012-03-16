
<div class="info-block">
    <table>
        <tr>
            <th>ID</th><th>Revision Name</th><th>Accepted Revison Indexes</th>
        </tr>
        <#list sets as set>
            <tr>
                <td>${set.id}</td>
                <td>${set.name}</td>
                <td>${set.revisionIndexString}</td>
            </tr>
        </#list>
    </table>      
</div>
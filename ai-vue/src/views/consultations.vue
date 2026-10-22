<template>
    <div style="display: flex; flex-direction: column; height: 100%">
        <PageHead title="咨询记录" />
        <el-table :data="tableData" style="width: 100%">
                <el-table-column prop="id" label="会话ID" width="100"/>
                <el-table-column label="用户" width="100">
                    <template #default="scope">
                        <el-avatar>{{ scope.row.userNickname?.charAt(0) }}</el-avatar>
                    </template>
                </el-table-column>
                <el-table-column label="情绪日志">
                    <template #default="scope">
                        <div class="session-title">{{ scope.row.sessionTitle }}</div>
                        <div class="session-preview">{{ scope.row.lastMessageContent }}</div>
                    </template>
                </el-table-column>
                <el-table-column prop="messageCount" label="消息数" width="100"/>
                <el-table-column prop="startedAt" label="时间" width="170"/>
                <el-table-column label="操作" width="140">
                    <template #default="scope">
                        <el-button type="primary" text @click="viewSessionDetail">详情</el-button>
                        <el-button link type="danger" size="small">删除</el-button>
                    </template>
                </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: center; margin-top: auto; padding: 16px 0">
            <el-pagination
                v-model:current-page="pagination.currentPage"
                v-model:page-size="pagination.pageSize"
                :total="pagination.total"
                layout="prev, pager, next"
                @current-change="handlePageChange"
            />
        </div>
    </div>
</template>
<script setup>
import  {onMounted, ref, reactive} from 'vue'
import PageHead from '@/components/PageHead.vue'
import {getConsultationPage} from '@/api/admin'

const tableData = ref([])
const pagination = reactive({
    currentPage: 1,
    pageSize: 10,
    total: 0,
})
const getList = ()=>{
    getConsultationPage(pagination).then(res=>{
        const {records,  total} = res.data
        tableData.value = records
        pagination.total = total
    })
}
onMounted(()=>{
    getList()
})
// 翻页回调
const handlePageChange = ()=>{
    getList()
}
const viewSessionDetail = () => {}
</script>
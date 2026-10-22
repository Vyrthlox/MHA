<template>
    <div class="emotional" style="display: flex; flex-direction: column; height: 100%">
        <PageHead title="情绪日志" />
        <TableSearch :fromItem="fromItem" @search="handleSearch" />
        <el-table :data="tableData" style="width: 100%; margin-top: 16px">
            <el-table-column prop="id" label="ID" width="100" align="center" />
            <el-table-column label="用户" align="center">
                <template #default="scope">
                    <el-avatar>{{ scope.row.userNickname?.charAt(0) }}</el-avatar>
                </template>
            </el-table-column>
            <el-table-column prop="recordDate" label="记录日期" align="center" />
            <el-table-column prop="moodScore" label="情绪评分" align="center" />
            <el-table-column prop="lifeIndex" label="生活指标" align="center" />
            <el-table-column prop="triggerFactor" label="情绪触发因素" align="center" />
            <el-table-column prop="diaryContent" label="日记内容" align="center" />
        </el-table>
        <div style="display: flex; justify-content: center; margin-top: auto; padding: 16px 0">
            <el-pagination
                v-model:current-page="pagination.currentPage"
                v-model:page-size="pagination.size"
                :total="pagination.total"
                layout="prev, pager, next"
                @current-change="handlePageChange"
            />
        </div>
    </div>
</template>
<script setup>
import {ref, reactive} from 'vue'
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'

const fromItem=[
    {comp:'input',prop:'userID',label:'用户ID',placeholder:'请输入用户ID'},
    {comp:'select',prop:'moodScoreRange',label:'情绪评分',placeholder:'请选择评分范围',options:[
        {label:'低分1-3',value:'1-3'},
        {label:'中分4-6',value:'4-6'},
        {label:'高分7-10',value:'7-10'}
    ]}
]

const pagination = reactive({
    currentPage: 1,
    size: 10,
    total: 35
})

// 保存最近一次的查询条件，翻页时复用
const searchForm = reactive({})

const getList = ()=>{
    const params = {
        ...pagination,
        ...searchForm
    }
    console.log(params,'请求参数')
    // 接口接好后在这里请求情绪日志列表：把列表赋给 tableData，把总数赋给 pagination.total
}

const handleSearch = (formData)=>{
    Object.assign(searchForm,formData)
    pagination.currentPage = 1
    getList()
}

// 翻页回调
const handlePageChange = (page)=>{
    pagination.currentPage = page
    getList()
}

const tableData = ref([
    { id: 1, userNickname: 'TestUser', recordDate: '2026-08-31', moodScore: 4, lifeIndex: '睡眠不足', triggerFactor: '课业压力', diaryContent: '今天作业太多，有点焦虑。' },
    { id: 2, userNickname: 'TestUser', recordDate: '2026-08-30', moodScore: 8, lifeIndex: '作息规律', triggerFactor: '朋友陪伴', diaryContent: '和朋友聊了很久，心情不错。' },
    { id: 3, userNickname: 'TestUser', recordDate: '2026-08-29', moodScore: 6, lifeIndex: '饮食一般', triggerFactor: '人际关系', diaryContent: '和室友有点小摩擦。' }
])
</script>

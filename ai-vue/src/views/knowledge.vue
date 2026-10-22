<template>
    <div style="display: flex; flex-direction: column; height: 100%">
        <PageHead title="知识文章" >
            <template #buttons>
                <el-button type="primary" size="small" @click="handleAdd">新增</el-button>
                <el-button type="primary" size="small">删除</el-button>
            </template>
        </PageHead>
        <TableSearch :fromItem="fromItem" @search="handleSearch"></TableSearch>
        <el-table :data="tableData" style="width: 100%; margin-top: 16px">
            <el-table-column prop="title" label="文章标题" align="center" />
            <el-table-column prop="categoryName" label="分类" align="center" />
            <el-table-column prop="author" label="作者" align="center" />
            <el-table-column prop="readCount" label="阅读量" align="center" />
            <el-table-column prop="publishTime" label="发布时间" align="center" />
            <el-table-column label="操作" align="center">
                <template #default="scope">
                    <el-button link type="primary" size="small">编辑</el-button>
                    <el-button v-if="scope.row.status === 1" link type="warning" size="small">下线</el-button>
                    <el-button v-if="scope.row.status === 1" link type="danger" size="small">删除</el-button>
                    <el-button v-if="scope.row.status !== 1" link type="success" size="small">发布</el-button>
                </template>
            </el-table-column>
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

        <el-dialog v-model="dialogVisible" title="新增文章" width="760px">
            <el-form :model="form" label-width="90px">
                <el-form-item label="文章标题">
                    <el-input v-model="form.title" placeholder="请输入文章标题" />
                </el-form-item>
                <el-form-item label="分类">
                    <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                        <el-option v-for="item in categories" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="文章摘要">
                    <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="请输入文章摘要" />
                </el-form-item>
                <el-form-item label="标签">
                    <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
                </el-form-item>
                <el-form-item label="封面图片">
                    <el-upload list-type="picture-card" :auto-upload="false" :limit="1">
                        <el-icon><Plus /></el-icon>
                    </el-upload>
                </el-form-item>
                <el-form-item label="文章内容">
                    <div style="border: 1px solid #dcdfe6; width: 100%">
                        <Toolbar
                            :editor="editorRef"
                            :defaultConfig="toolbarConfig"
                            mode="default"
                            style="border-bottom: 1px solid #dcdfe6"
                        />
                        <Editor
                            v-model="form.content"
                            :defaultConfig="editorConfig"
                            mode="default"
                            style="height: 300px; overflow-y: hidden"
                            @onCreated="handleCreated"
                        />
                    </div>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary">确定</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
    import {onMounted, onBeforeUnmount, ref,reactive,shallowRef} from 'vue'
    import {Editor, Toolbar} from '@wangeditor/editor-for-vue'
    import '@wangeditor/editor/dist/css/style.css'
    import PageHead from '@/components/PageHead.vue'
    import TableSearch from '@/components/TableSearch.vue'
    import {categoryTree} from '@/api/admin'

    const fromItem=reactive([{
        comp: 'input',
        prop: 'title',
        label: '文章标题',
        placeholder: '请输入文章标题'
    },{
        comp: 'select',
        prop: 'categoryId',
        label: '分类',
        placeholder:'请选择文章分类'
    },{
        comp:'select',
        prop:'status',
        label:'状态',
        placeholder:'请输入内容',
        options:[
            {   label:'已下线',
                value:2
            },
            {
                label:'已发布',
                value:1
            },
            {
                label:'草稿',
                value:0
            }
        ]
    }])
//分页参数
    const pagination = reactive({
        currentPage:1,
        size:10,
        total:35
    })

    // 保存最近一次的查询条件，翻页时复用
    const searchForm = reactive({})

    const getList = ()=>{
        const params = {
            ...pagination,
            ...searchForm
        }
        console.log(params,'请求参数')
        // 接口接好后在这里请求文章列表：把列表赋给 tableData，把总数赋给 pagination.total
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

    // 新增弹窗
    const dialogVisible = ref(false)
    const form = reactive({
        title: '',
        categoryId: '',
        summary: '',
        tags: '',
        content: ''
    })
    const handleAdd = ()=>{
        dialogVisible.value = true
    }

    // 富文本编辑器
    const editorRef = shallowRef()
    const toolbarConfig = {}
    const editorConfig = { placeholder: '请输入文章内容...' }
    const handleCreated = (editor)=>{
        editorRef.value = editor
    }
    onBeforeUnmount(()=>{
        const editor = editorRef.value
        if(editor) editor.destroy()
    })

    const categoryMap = reactive({})
    const categories = ref([])

    const tableData = [
        { title: '如何缓解焦虑情绪', categoryName: '情绪管理', author: '张三', readCount: 128, publishTime: '2025-09-01 10:20', status: 1 },
        { title: '人际关系中的边界感', categoryName: '人际关系', author: '李四', readCount: 356, publishTime: '2025-09-03 15:42', status: 0 },
        { title: '认识你的压力来源', categoryName: '压力缓解', author: '王五', readCount: 92, publishTime: '2025-09-05 09:08', status: 0 }
    ]

    onMounted(async()=>{
        const res = await categoryTree()
        categories.value = res.data.map(item=>{
            categoryMap[item.id] = item.categoryName
            return{
                label:item.categoryName,
                value: item.id
            }
        })
        fromItem[1].options = categories.value
    })
</script>
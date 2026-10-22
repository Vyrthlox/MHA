<template>
    <el-form ref="ruleFormRef" :model="formData">  
        <el-row :gutter="24">
            <template v-for="item in formItemAttrs" :key="item.prop">
                <el-col v-bind="item.col">
                        <el-form-item :label="item.label" :prop="item.prop">
                            <component v-model="formData[item.prop]" :is="isComp(item.comp)" :placeholder="item.placeholder">
                                <template v-if="item.comp === 'select'">
                                    <el-option label="全部" value=""/>
                                    <el-option 
                                        v-for="opt in item.options"
                                        :key="opt.value"
                                        :label="opt.label"
                                        :value="opt.value"/>
                                </template>
                            </component>
                    </el-form-item>
                </el-col>

            </template>
        </el-row> 
        <el-row>
            <el-button type="primary" size="small" @click="handleSearch">查询</el-button>
            <el-button type="primary" size="small" @click="handleReset(ruleFormRef)">重置</el-button>
        </el-row>

    </el-form>
</template>

<script setup>
import {ref, reactive,computed} from 'vue'
// 计算属性，根据props动态添加col属性

const props = defineProps({
    fromItem:{
        type:Array,
        default:()=>[]
    }
})

const emit = defineEmits(['search'])

// 定义表单数据
const formData = reactive({})

const isComp = (comp)=>{
    return{
        input:'elInput',
        select:'elSelect'
    }[comp]
}
// 查询
const handleSearch = ()=>{
    //console.log(formData)
    emit('search',formData)
}
// 重置
const handleReset = (formEl)=>{
    //先重置表单
    if(!formEl) return 
    formEl.resetFields()
    emit('search',formData)
}
// 计算属性，根据props动态添加col属性
const formItemAttrs = computed(()=>{
    const { fromItem: formItem } = props
    formItem.forEach(item=>{
        item.col={xs:24,sm:12,md:       8,lg:6,xl:6}
    })
    return formItem
})

// 定义表单实例
const ruleFormRef = ref(null)
</script>
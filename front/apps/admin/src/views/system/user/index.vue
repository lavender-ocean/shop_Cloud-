<template>
  <div>
    <h3>用户管理</h3>
    <el-button v-permission="'sys:user:add'" type="primary">新增用户</el-button>
    <el-button v-permission="'sys:user:edit'" type="warning">编辑用户</el-button>
    <el-button v-permission="'sys:user:delete'" type="danger">删除用户</el-button>
    <p style="color:#999">（无权限的按钮会被 v-permission 移除）</p>
  </div>
</template>
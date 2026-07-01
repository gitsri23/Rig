package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Int): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun insert(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun update(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    suspend fun delete(project: ProjectEntity) {
        projectDao.deleteProject(project)
    }
}

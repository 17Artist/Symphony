import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid(
  defineConfig({
    title: 'Symphony',
    description: 'Symphony 属性框架文档',
    themeConfig: {
      nav: [
        { text: '指南', link: '/guide/01-quick-start' },
        { text: '设计', link: '/design/01-architecture' }
      ],
      sidebar: {
        '/guide/': [
          {
            text: '使用指南',
            items: [
              { text: '快速开始', link: '/guide/01-quick-start' },
              { text: '属性配置', link: '/guide/02-attribute-config' },
              { text: '词条配置', link: '/guide/03-affix-config' },
              { text: '触发器参考', link: '/guide/04-trigger-reference' },
              { text: '技能提供者', link: '/guide/05-skill-provider-guide' },
              { text: '成长系统', link: '/guide/06-growth-config' },
              { text: '脚本示例', link: '/guide/07-script-examples' },
              { text: '高级指南', link: '/guide/08-advanced-guide' },
              { text: '事件 API', link: '/guide/09-events-api' },
              { text: '扩展开发', link: '/guide/10-extending' }
            ]
          }
        ],
        '/design/': [
          {
            text: '设计文档',
            items: [
              { text: '架构总览', link: '/design/01-architecture' },
              { text: '属性系统', link: '/design/02-attribute-system' },
              { text: '词条系统', link: '/design/03-affix-system' },
              { text: '触发器系统', link: '/design/04-trigger-system' },
              { text: '技能提供者', link: '/design/05-skill-provider' },
              { text: '成长系统', link: '/design/06-growth-system' },
              { text: '脚本集成', link: '/design/07-script-integration' },
              { text: '数据存储', link: '/design/08-data-storage' },
              { text: 'API 设计', link: '/design/09-api-design' },
              { text: '高级系统', link: '/design/10-advanced-systems' },
            ]
          }
        ]
      }
    }
  }),
  {
    mermaid: {},
    mermaidPlugin: {
      class: 'mermaid'
    }
  }
)
